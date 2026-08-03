defmodule Mix.Tasks.CompareCitations do
  @moduledoc """
  Compares the materialized `cpmeta:hasBiblioInfo` values between the local Virtuoso
  instance (`VIRTUOSO_HOST`) and another SPARQL endpoint — e.g. to check the
  Elixir populator's output against production.

  Streams the distinct subjects having `cpmeta:hasBiblioInfo` out of a local
  named graph, then looks up that value on both endpoints for exactly those
  subjects and compares the decoded JSON — printing every mismatch with both
  sides' values. Other materialized citation triples are not compared because
  `hasBiblioInfo` is built from them.

  The remote lookup binds `?s` (in batches, via `VALUES`) rather than
  streaming `?s ?p ?o` unbound: on production, `hasBiblioInfo` is computed on
  the fly per subject rather than stored as an ordinary triple, so an unbound
  query matches nothing there — the subject has to be bound for the computation
  to run, but batching many subjects into one `VALUES` list still works and
  keeps the request count down.

  The comparison is chunked and concurrent: each chunk of subjects coming out
  of the scan is looked up on both sides at once and compared right away, so
  the two sides overlap, several chunks are in flight at a time, and only the
  mismatches — not every subject's values — are held in memory.

  A remote batch whose response contains invalid JSON is retried and, when it
  looks like a streamed computation failed midway or the result was truncated,
  bisected until the smaller queries succeed or a single failing subject is
  found. That subject is reported as a remote error and left out of the
  comparison; the run continues.

  ## Usage

      mix compare_citations --endpoint https://meta.icos-cp.eu/sparql

  ## Options

    * `--endpoint` (required) — the other SPARQL endpoint's query URL.
    * `--graph` — the local named graph to read; defaults to the local
      `DERIVED_CITATIONS_GRAPH` configuration.
    * `--batch-size` — how many subjects to bind per remote request;
      defaults to 100. The remote computes its values per subject, so this
      stays small.
    * `--local-batch-size` — how many subjects to bind per local request,
      and the size of one compared chunk; defaults to 1000. The local graph
      holds ordinary stored triples, so it takes bigger batches happily.
    * `--concurrency` — how many chunks to compare at a time; defaults to 4.
      Each in-flight chunk means at most one request per endpoint, so this is
      also the concurrent request count each side sees.
    * `--mismatch-log` — file to which mismatches are written as chunks
      complete; defaults to `compare_citations_mismatches.log`. The file is
      replaced at the start of each run.
  """
  @shortdoc "Diffs materialized hasBiblioInfo JSON against another SPARQL endpoint"

  use Mix.Task

  alias BiblioMaterializer.{Sparql, Vocab}

  @default_batch_size 100
  @default_local_batch_size 1_000
  @default_concurrency 4
  @default_mismatch_log "compare_citations_mismatches.log"
  @local_page_size 5_000
  @decode_retry_delays [250, 1_000]
  @max_isolation_batches 64

  @impl Mix.Task
  def run(argv) do
    {opts, _rest} =
      OptionParser.parse!(argv,
        strict: [
          endpoint: :string,
          graph: :string,
          batch_size: :integer,
          local_batch_size: :integer,
          concurrency: :integer,
          mismatch_log: :string
        ]
      )

    endpoint = opts[:endpoint] || Mix.raise("--endpoint <sparql endpoint URL> is required")
    batch_size = positive_option!(opts, :batch_size, @default_batch_size)
    local_batch_size = positive_option!(opts, :local_batch_size, @default_local_batch_size)
    concurrency = positive_option!(opts, :concurrency, @default_concurrency)
    mismatch_log = opts[:mismatch_log] || @default_mismatch_log

    # `loadconfig` alone only merges config/config.exs — config/runtime.exs
    # (where VIRTUOSO_HOST etc. are read from the environment) is otherwise
    # only ever read by `mix run` and releases, so read it the same way here.
    Mix.Task.run("compile")
    Mix.Task.run("loadconfig")

    "config/runtime.exs"
    |> Config.Reader.read!(env: Mix.env(), target: Mix.target())
    |> Application.put_all_env()

    # This task only reads, so start the HTTP client and its dependencies
    # without starting the populator application.
    {:ok, _} = Application.ensure_all_started(:req)

    local_graph =
      opts[:graph] || Application.fetch_env!(:biblio_materializer, :derived_citations_graph)

    local_host = Application.fetch_env!(:biblio_materializer, :virtuoso_host)

    Mix.shell().info(
      "Comparing #{local_host}/sparql (graph #{local_graph}) against #{endpoint}: " <>
        "chunks of #{local_batch_size} subjects, remote batches of #{batch_size}, " <>
        "#{concurrency} chunks in flight\n" <>
        "Writing mismatches to #{Path.expand(mismatch_log)}"
    )

    File.open!(mismatch_log, [:write, :utf8], fn log ->
      write_log_header(log, local_host, local_graph, endpoint)

      result =
        local_graph
        |> subject_stream()
        |> Stream.chunk_every(local_batch_size)
        |> Task.async_stream(&compare_chunk(&1, local_graph, endpoint, batch_size),
          max_concurrency: concurrency,
          ordered: false,
          timeout: :infinity
        )
        |> Enum.reduce(
          {0, 0, [], 0, []},
          &accumulate_chunk(&1, &2, log)
        )

      {matches, mismatch_count, _mismatches, _error_count, errors} = result
      write_log_summary(log, result)
      report(matches, mismatch_count, errors)
    end)
  end

  defp predicates, do: [Vocab.has_biblio_info()]

  defp positive_option!(opts, key, default) do
    case Keyword.get(opts, key, default) do
      value when is_integer(value) and value > 0 ->
        value

      _value ->
        option = key |> Atom.to_string() |> String.replace("_", "-")
        Mix.raise("--#{option} must be a positive integer")
    end
  end

  defp values(uris), do: Enum.map_join(uris, " ", &"<#{&1}>")

  # Page only the distinct subject URIs. Sorting biblio JSON is extremely
  # expensive in Virtuoso, and ordering by subject alone is only deterministic
  # if each subject occurs once. DISTINCT gives us exactly that. Once a subject
  # chunk is known, VALUES lookups fetch every value without any ordering or
  # result-set truncation risk.
  #
  # The scan itself stays sequential — cursor offsets are inherently so — but
  # it is a page per few thousand subjects, and it runs while the chunks it
  # already produced are being compared.
  defp subject_stream(graph) do
    alternatives =
      predicates()
      |> Enum.map_join(" UNION ", &"{ ?s <#{&1}> ?o }")

    subject_query = """
    SELECT DISTINCT ?s WHERE {
      GRAPH <#{graph}> {
        #{alternatives}
      }
    }
    """

    subject_query
    |> Sparql.select_stream("?s", nil,
      page_size: @local_page_size,
      on_page: &print_local_page/2
    )
    |> Stream.map(& &1["s"]["value"])
  end

  defp print_local_page(offset, 0) do
    Mix.shell().info("  Finished local subject scan (#{offset} subjects)")
  end

  defp print_local_page(_offset, _count), do: :ok

  # One chunk of subjects, both sides at once: the local lookup runs in its
  # own task while this process walks the chunk's remote batches, so a chunk
  # costs the slower of the two sides rather than their sum.
  defp compare_chunk(subjects, graph, endpoint, batch_size) do
    local = Task.async(fn -> fetch_local(subjects, graph) end)
    {remote, errors} = fetch_remote(subjects, endpoint, batch_size)

    {matches, mismatches} =
      local
      |> Task.await(:infinity)
      |> Map.drop(Enum.map(errors, fn {uri, _message} -> uri end))
      |> compare(remote)

    {matches, mismatches, errors}
  end

  defp fetch_local(subjects, graph) do
    query = """
    SELECT ?s ?p ?o WHERE {
      GRAPH <#{graph}> {
        VALUES ?s { #{values(subjects)} }
        VALUES ?p { #{values(predicates())} }
        ?s ?p ?o .
      }
    }
    """

    query
    |> Sparql.select()
    |> Enum.reduce(%{}, &add_triple/2)
  end

  # Remote: hasBiblioInfo is computed per subject rather than stored, so an
  # unbound query matches nothing there — ?s has to be bound for the computation
  # to fire. Subjects are batched into one VALUES list per request to keep the
  # request count down. The batches of one chunk run one after the other;
  # concurrency comes from the chunks themselves, so that the endpoint sees a
  # bounded number of these expensive queries.
  #
  # Returns the values found and the subjects the endpoint could not answer
  # for (see `remote_batch/2`); those are left out of the comparison, since
  # nothing is known about their remote side either way.
  defp fetch_remote(subjects, endpoint, batch_size) do
    subjects
    |> Enum.chunk_every(batch_size)
    |> Enum.reduce({%{}, []}, fn batch, {values, errors} ->
      {batch_values, batch_errors} = remote_batch(batch, endpoint)
      {Map.merge(values, batch_values), batch_errors ++ errors}
    end)
  end

  # A batch whose response does not decode is bisected down to the single
  # subject that breaks it: the endpoint computes its values as it streams
  # the results, so one subject failing mid-computation corrupts the body of
  # every batch it is part of. Reporting that subject — and comparing all the
  # others — beats losing the whole run to it. A malformed response is retried
  # first. Invalid bytes after the JSON document has started and truncated JSON
  # documents are narrowed: the latter can also mean that a valid result crossed
  # the endpoint's response-size limit, in which case both smaller halves simply
  # succeed. Isolation has a request budget in case every response is persistently
  # malformed. Transport and HTTP errors have already exhausted `Sparql`'s retries
  # and say nothing about a particular subject.
  defp remote_batch(subjects, endpoint) do
    {values, errors, _budget} =
      remote_batch(subjects, endpoint, @max_isolation_batches)

    {values, errors}
  end

  defp remote_batch(subjects, _endpoint, 0) do
    message = "remote JSON failure isolation stopped after #{@max_isolation_batches} batches"
    {%{}, Enum.map(subjects, &{&1, message}), 0}
  end

  defp remote_batch(subjects, endpoint, budget) do
    {request_remote_batch(subjects, endpoint, @decode_retry_delays), [], budget - 1}
  rescue
    error in Sparql.DecodeError ->
      if isolatable_decode_error?(error) do
        isolate_remote_batch(subjects, endpoint, error, budget - 1)
      else
        reraise error, __STACKTRACE__
      end
  end

  defp request_remote_batch(subjects, endpoint, retry_delays) do
    query = """
    SELECT ?s ?p ?o WHERE {
      VALUES ?s { #{values(subjects)} }
      VALUES ?p { #{values(predicates())} }
      ?s ?p ?o .
    }
    """

    query
    |> Sparql.select(endpoint)
    |> Enum.reduce(%{}, &add_triple/2)
  rescue
    error in Sparql.DecodeError ->
      case {error.reason, retry_delays} do
        # A large result ending exactly at EOF is commonly a deterministic
        # endpoint response-size cap. Confirm it once, then let the caller
        # split the batch instead of repeating the same oversized request.
        {{:unexpected_end, _offset}, [delay | _rest]} ->
          Process.sleep(delay)
          request_remote_batch(subjects, endpoint, [])

        {_reason, [delay | rest]} ->
          Process.sleep(delay)
          request_remote_batch(subjects, endpoint, rest)

        {_reason, []} ->
          reraise error, __STACKTRACE__
      end
  end

  defp isolatable_decode_error?(%Sparql.DecodeError{
         reason: {:invalid_byte, offset, _byte},
         body: body
       }) do
    offset >= 64 and json_object?(body)
  end

  defp isolatable_decode_error?(%Sparql.DecodeError{
         reason: {:unexpected_end, offset},
         body: body
       }) do
    offset >= 64 and offset == byte_size(body) and json_object?(body)
  end

  defp isolatable_decode_error?(_error), do: false

  defp json_object?(<<char, rest::binary>>) when char in [?\s, ?\t, ?\r, ?\n],
    do: json_object?(rest)

  defp json_object?(<<?{, _rest::binary>>), do: true
  defp json_object?(_body), do: false

  defp isolate_remote_batch([subject], _endpoint, error, budget) do
    {%{}, [{subject, Exception.message(error)}], budget}
  end

  defp isolate_remote_batch(subjects, endpoint, _error, budget) do
    {left, right} = Enum.split(subjects, div(length(subjects), 2))
    {left_values, left_errors, budget} = remote_batch(left, endpoint, budget)
    {right_values, right_errors, budget} = remote_batch(right, endpoint, budget)
    {Map.merge(left_values, right_values), left_errors ++ right_errors, budget}
  end

  defp add_triple(row, acc) do
    s = row["s"]["value"]
    p = row["p"]["value"]
    o = row["o"]["value"]
    add_value(acc, s, p, o)
  end

  defp add_value(acc, subject, predicate, object) do
    Map.update(acc, subject, %{predicate => MapSet.new([object])}, fn predicates ->
      Map.update(predicates, predicate, MapSet.new([object]), &MapSet.put(&1, object))
    end)
  end

  defp compare(local, remote) do
    Enum.reduce(local, {0, []}, fn {uri, l}, {matches, mismatches} ->
      r = Map.get(remote, uri, %{})

      if equivalent?(l, r),
        do: {matches + 1, mismatches},
        else: {matches, [{uri, l, r} | mismatches]}
    end)
  end

  @doc false
  def equivalent?(local, remote) do
    predicate = Vocab.has_biblio_info()
    json_values_equal?(Map.get(local, predicate), Map.get(remote, predicate))
  end

  defp json_values_equal?(local, remote) do
    json_value_sets_equal?(decode_json_values(local), decode_json_values(remote))
  end

  defp decode_json_values(nil), do: nil

  defp decode_json_values(%MapSet{} = values) do
    values
    |> Enum.map(&Jason.decode!/1)
    |> Enum.uniq()
  end

  defp json_value_sets_equal?(nil, nil), do: true
  defp json_value_sets_equal?(nil, _remote), do: false
  defp json_value_sets_equal?(_local, nil), do: false

  defp json_value_sets_equal?(local, remote) do
    length(local) == length(remote) and
      Enum.all?(local, fn local_value ->
        Enum.any?(remote, &(&1 == local_value))
      end)
  end

  defp accumulate_chunk(
         {:ok, {chunk_matches, chunk_mismatches, chunk_errors}},
         {matches, mismatch_count, mismatches, error_count, errors},
         log
       ) do
    write_mismatches(log, chunk_mismatches)
    print_mismatches(chunk_mismatches)

    acc = {
      matches + chunk_matches,
      mismatch_count + length(chunk_mismatches),
      chunk_mismatches ++ mismatches,
      error_count + length(chunk_errors),
      chunk_errors ++ errors
    }

    print_progress(acc)
    acc
  end

  defp accumulate_chunk({:exit, reason}, _acc, _log) do
    Mix.raise("citation comparison worker failed: #{Exception.format_exit(reason)}")
  end

  defp print_progress({matches, mismatch_count, _mismatches, error_count, _errors}) do
    compared = matches + mismatch_count

    unanswered =
      case error_count do
        0 -> ""
        n -> ", #{n} unanswered by the remote"
      end

    Mix.shell().info(
      "  Compared #{compared} subjects (#{mismatch_count} mismatching#{unanswered})"
    )
  end

  defp report(matches, mismatch_count, errors) do
    Mix.shell().info("")

    errors
    |> Enum.sort_by(fn {uri, _message} -> uri end)
    |> Enum.each(&print_error/1)

    unanswered =
      case length(errors) do
        0 -> ""
        n -> ", #{n} unanswered by the remote"
      end

    Mix.shell().info("#{matches} matching subjects, #{mismatch_count} mismatching#{unanswered}")
  end

  defp print_error({uri, message}) do
    Mix.shell().info("REMOTE ERROR #{uri}")
    Mix.shell().info("  #{message}")
    Mix.shell().info("")
  end

  defp print_mismatch({uri, local, remote}) do
    {uri, local, remote}
    |> mismatch_text(:console)
    |> IO.iodata_to_binary()
    |> String.trim_trailing()
    |> Mix.shell().info()
  end

  defp print_mismatches(mismatches) do
    mismatches
    |> Enum.sort_by(fn {uri, _local, _remote} -> uri end)
    |> Enum.each(&print_mismatch/1)
  end

  defp write_log_header(log, local_host, local_graph, endpoint) do
    IO.write(log, """
    # Citation mismatch log
    # Started: #{DateTime.utc_now() |> DateTime.truncate(:second) |> DateTime.to_iso8601()}
    # Local: #{local_host}/sparql (graph #{local_graph})
    # Remote: #{endpoint}

    """)
  end

  defp write_mismatches(log, mismatches) do
    mismatches
    |> Enum.sort_by(fn {uri, _local, _remote} -> uri end)
    |> Enum.each(&IO.write(log, mismatch_text(&1, :log)))
  end

  defp write_log_summary(
         log,
         {matches, mismatch_count, _mismatches, error_count, _errors}
       ) do
    IO.write(
      log,
      "# Completed: #{matches} matching, #{mismatch_count} mismatching, " <>
        "#{error_count} unanswered by remote\n"
    )
  end

  @doc false
  def mismatch_text({uri, local, remote}, destination) do
    predicate = Vocab.has_biblio_info()

    differences =
      json_key_differences(
        Map.get(local, predicate),
        Map.get(remote, predicate),
        destination
      )

    [
      format_heading("MISMATCH #{uri}\n", :yellow, destination),
      format_heading("  hasBiblioInfo\n", :cyan, destination),
      differences,
      "\n"
    ]
  end

  defp json_key_differences(local, remote, destination) do
    {local, remote} = json_objects_for_diff(local, remote)

    keys =
      local
      |> Map.keys()
      |> Kernel.++(Map.keys(remote))
      |> Enum.uniq()
      |> Enum.sort()
      |> Enum.reject(fn key ->
        Map.get(local, key, {:missing}) == Map.get(remote, key, {:missing})
      end)

    local_diff = ordered_subset(local, keys)
    remote_diff = ordered_subset(remote, keys)

    json_diff(local_diff, remote_diff, destination)
  end

  defp ordered_subset(values, keys) do
    keys
    |> Enum.flat_map(fn key ->
      case Map.fetch(values, key) do
        {:ok, value} -> [{key, value}]
        :error -> []
      end
    end)
    |> Jason.OrderedObject.new()
  end

  defp json_diff(local, remote, destination) do
    local_lines = local |> Jason.encode!(pretty: true) |> String.split("\n")
    remote_lines = remote |> Jason.encode!(pretty: true) |> String.split("\n")

    lines =
      local_lines
      |> List.myers_difference(remote_lines)
      |> refine_line_differences()

    [
      format_heading("    --- local\n", :red, destination),
      format_heading("    +++ remote\n", :green, destination),
      format_diff_lines(lines, destination)
    ]
  end

  defp refine_line_differences([{:del, deleted}, {:ins, inserted} | rest]) do
    pair_count = min(length(deleted), length(inserted))
    {paired_deleted, remaining_deleted} = Enum.split(deleted, pair_count)
    {paired_inserted, remaining_inserted} = Enum.split(inserted, pair_count)

    changes =
      Enum.zip_with(paired_deleted, paired_inserted, fn deleted, inserted ->
        {:change, deleted, inserted,
         List.myers_difference(String.graphemes(deleted), String.graphemes(inserted))}
      end)

    changes ++
      Enum.map(remaining_deleted, &{:del, &1}) ++
      Enum.map(remaining_inserted, &{:ins, &1}) ++ refine_line_differences(rest)
  end

  defp refine_line_differences([{kind, lines} | rest]) do
    Enum.map(lines, &{kind, &1}) ++ refine_line_differences(rest)
  end

  defp refine_line_differences([]), do: []

  defp format_diff_lines(lines, :log) do
    Enum.map(lines, fn
      {:change, deleted, inserted, character_diff} ->
        [
          "    -",
          deleted,
          "\n",
          "    ?",
          character_marker(character_diff, :del),
          "\n",
          "    +",
          inserted,
          "\n",
          "    ?",
          character_marker(character_diff, :ins),
          "\n"
        ]

      {kind, line} ->
        ["    ", diff_prefix(kind), line, "\n"]
    end)
  end

  defp format_diff_lines(lines, :console) do
    Enum.map(lines, fn
      {:change, _deleted, _inserted, character_diff} ->
        [
          format_character_diff(character_diff, :del),
          format_character_diff(character_diff, :ins)
        ]

      {:del, line} ->
        format_heading(["    -", line, "\n"], :red, :console)

      {:ins, line} ->
        format_heading(["    +", line, "\n"], :green, :console)

      {:eq, line} ->
        ["     ", line, "\n"]
    end)
  end

  defp diff_prefix(:eq), do: " "
  defp diff_prefix(:del), do: "-"
  defp diff_prefix(:ins), do: "+"

  defp character_marker(character_diff, side) do
    character_diff
    |> Enum.map(fn
      {:eq, graphemes} -> String.duplicate(" ", length(graphemes))
      {^side, graphemes} -> String.duplicate("^", length(graphemes))
      {_other_side, _graphemes} -> ""
    end)
    |> IO.iodata_to_binary()
    |> String.trim_trailing()
  end

  defp format_character_diff(character_diff, side) do
    {prefix, color} = if side == :del, do: {"-", :red}, else: {"+", :green}

    content =
      Enum.flat_map(character_diff, fn
        {:eq, graphemes} ->
          graphemes

        {^side, graphemes} ->
          [
            IO.ANSI.underline(),
            graphemes,
            IO.ANSI.no_underline(),
            apply(IO.ANSI, color, []),
            IO.ANSI.bright()
          ]

        {_other_side, _graphemes} ->
          []
      end)

    format_heading(["    ", prefix, content, "\n"], color, :console)
  end

  defp format_heading(content, _color, :log), do: content

  defp format_heading(content, color, :console) do
    [apply(IO.ANSI, color, []), IO.ANSI.bright(), content, IO.ANSI.reset()]
  end

  defp json_objects_for_diff(local, remote) do
    case {decode_json_values(local), decode_json_values(remote)} do
      {[local], [remote]} when is_map(local) and is_map(remote) ->
        {local, remote}

      {[local], nil} when is_map(local) ->
        {local, %{}}

      {nil, [remote]} when is_map(remote) ->
        {%{}, remote}

      {local, remote} ->
        {%{"$values" => local || {:missing}}, %{"$values" => remote || {:missing}}}
    end
  end
end
