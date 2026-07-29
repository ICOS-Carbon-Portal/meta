defmodule Mix.Tasks.CompareCitations do
  @moduledoc """
  Compares the materialized citation triples between the local Virtuoso
  instance (`VIRTUOSO_HOST`) and another SPARQL endpoint — e.g. to check the
  Elixir populator's output against production.

  Streams the distinct subjects having `cpmeta:hasCitationString`,
  `cpmeta:hasBiblioInfo` or `dcterms:license` (the predicates
  `BiblioMaterializer.Writer` produces) out of a local named graph, then
  looks up all values on both endpoints for exactly those subjects and
  compares the two sides — printing every mismatch with both sides' values.

  The remote lookup binds `?s` (in batches, via `VALUES`) rather than
  streaming `?s ?p ?o` unbound: on production, `hasCitationString` and
  `hasBiblioInfo` are computed on the fly per subject rather than stored as
  ordinary triples, so an unbound query matches nothing there — the subject
  has to be bound for the computation to run, but batching many subjects
  into one `VALUES` list still works and keeps the request count down.

  ## Usage

      mix compare_citations --endpoint https://meta.icos-cp.eu/sparql

  ## Options

    * `--endpoint` (required) — the other SPARQL endpoint's query URL.
    * `--graph` — the local named graph to read; defaults to the local
      `DERIVED_CITATIONS_GRAPH` configuration.
    * `--batch-size` — how many subjects to bind per remote request;
      defaults to 200.
  """
  @shortdoc "Diffs materialized citation triples against another SPARQL endpoint"

  use Mix.Task

  alias BiblioMaterializer.{Sparql, Vocab}

  @default_batch_size 200
  @local_page_size 5_000

  @impl Mix.Task
  def run(argv) do
    {opts, _rest} =
      OptionParser.parse!(argv,
        strict: [endpoint: :string, graph: :string, batch_size: :integer]
      )

    endpoint = opts[:endpoint] || Mix.raise("--endpoint <sparql endpoint URL> is required")
    batch_size = opts[:batch_size] || @default_batch_size

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
      "Streaming local triples from #{local_host}/sparql (graph #{local_graph})..."
    )

    local = collect_local_subjects(local_graph)

    Mix.shell().info(
      "Looking up #{map_size(local)} subjects on #{endpoint} (batches of #{batch_size})..."
    )

    remote = collect_remote_subjects(Map.keys(local), endpoint, batch_size)

    report(local, remote)
  end

  defp predicates,
    do: [Vocab.has_citation_string(), Vocab.has_biblio_info(), Vocab.dcterms_license()]

  defp values(uris), do: Enum.map_join(uris, " ", &"<#{&1}>")

  # Page only the distinct subject URIs. Sorting citation strings and biblio
  # JSON is extremely expensive in Virtuoso, and ordering by subject alone is
  # only deterministic if each subject occurs once. DISTINCT gives us exactly
  # that. Once a subject batch is known, VALUES lookups fetch every value
  # without any ordering or result-set truncation risk.
  defp collect_local_subjects(graph) do
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
    |> Stream.chunk_every(@default_batch_size)
    |> Enum.reduce(%{}, fn subjects, acc ->
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
      |> Enum.reduce(acc, &add_triple/2)
    end)
  end

  defp print_local_page(offset, 0) do
    Mix.shell().info("  Finished local subject scan (#{offset} subjects)")
  end

  defp print_local_page(offset, count) do
    Mix.shell().info("  Received subjects #{offset + 1}-#{offset + count}")
  end

  # Remote: hasCitationString/hasBiblioInfo are computed per subject rather
  # than stored, so an unbound query matches nothing there — ?s has to be
  # bound for the computation to fire. Subjects are batched into one VALUES
  # list per request to keep the request count down.
  defp collect_remote_subjects(uris, endpoint, batch_size) do
    uris
    |> Enum.chunk_every(batch_size)
    |> Enum.reduce(%{}, fn batch, acc ->
      query = """
      SELECT ?s ?p ?o WHERE {
        VALUES ?s { #{values(batch)} }
        VALUES ?p { #{values(predicates())} }
        ?s ?p ?o .
      }
      """

      query
      |> Sparql.select(endpoint)
      |> Enum.reduce(acc, &add_triple/2)
    end)
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

  defp report(local, remote) do
    {matches, mismatches} =
      Enum.reduce(local, {0, []}, fn {uri, l}, {matches, mismatches} ->
        r = Map.get(remote, uri, %{})

        if l == r,
          do: {matches + 1, mismatches},
          else: {matches, [{uri, l, r} | mismatches]}
      end)

    Mix.shell().info("")

    mismatches
    |> Enum.sort_by(fn {uri, _l, _r} -> uri end)
    |> Enum.each(&print_mismatch/1)

    Mix.shell().info("#{matches} matching subjects, #{length(mismatches)} mismatching")
  end

  defp print_mismatch({uri, local, remote}) do
    Mix.shell().info("MISMATCH #{uri}")

    predicates()
    |> Enum.each(fn p ->
      l = Map.get(local, p)
      r = Map.get(remote, p)

      if l != r do
        Mix.shell().info("  #{predicate_label(p)}")
        Mix.shell().info("    local:  #{format_value(l)}")
        Mix.shell().info("    remote: #{format_value(r)}")
      end
    end)

    Mix.shell().info("")
  end

  defp predicate_label(p) do
    cond do
      p == Vocab.has_citation_string() -> "hasCitationString"
      p == Vocab.has_biblio_info() -> "hasBiblioInfo"
      p == Vocab.dcterms_license() -> "license"
      true -> p
    end
  end

  defp format_value(nil), do: "(missing)"

  defp format_value(%MapSet{} = values) do
    values
    |> Enum.sort()
    |> Enum.map_join(", ", &inspect/1)
  end
end
