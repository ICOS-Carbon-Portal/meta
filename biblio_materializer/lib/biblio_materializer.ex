defmodule BiblioMaterializer do
  @moduledoc """
  Populates the derived-citations named graph in Virtuoso with the biblio-info
  triples for all citable subjects (data objects, document objects and
  collections) — a standalone replacement for the Scala citations service's
  materializer, computing everything itself from raw SPARQL over HTTP and
  the DataCite REST API:

    * `cpmeta:hasBiblioInfo` — the subject's `References` as compact JSON
    * `cpmeta:hasCitationString` — the plain-text citation
    * `dcterms:license` — the licence IRI

  Subjects are processed in batches (READ_BATCH_SIZE, default 500) whose
  per-subject fields are read up front with one query per field group (see
  [`Subject`](`BiblioMaterializer.Subject`)) rather than a query per subject,
  concurrently within a batch (`--concurrency`, default 16). Their triples
  are written in batches too, rather than one INSERT DATA per subject — so a
  subject's triples land shortly after it is processed rather than
  immediately. DOI subjects are not handled inline: the worker
  emits their DataCite-independent triples (the licence), hands the rest to
  the [`DataCiteQueue`] GenServer and moves on; the queue fetches from
  DataCite (rate-limited by the queue itself) and writes the DataCite-
  dependent triples as the lookups complete. A run finishes when both the
  main pass and the queue are done.

  The subjects are streamed with a cursor-paged query (only their total
  comes from an up-front COUNT), so nothing large is fetched ahead of time
  and no server result-set cap can silently truncate the scan.

  Only subjects with no triples in the derived graph are populated; each
  batch asks the derived graph which of its own subjects are already there.
  Already-materialized subjects are left untouched (even if stale). Note that
  this includes DOI subjects whose DataCite lookups failed in an earlier run:
  their licence triple makes them count as materialized.
  """

  require Logger

  alias BiblioMaterializer.{DataCiteQueue, References, Run, Sparql, Subject, Vocab, Writer}

  @default_concurrency 16
  @write_concurrency 4

  @doc false
  def default_concurrency, do: @default_concurrency

  def run(opts \\ []) do
    graph = Application.fetch_env!(:biblio_materializer, :derived_citations_graph)
    concurrency = Keyword.get(opts, :concurrency, @default_concurrency)

    {:ok, run} = Run.start_link(concurrency: concurrency)
    context = Run.context(run)

    try do
      run_pass(graph, context, concurrency)
    after
      Supervisor.stop(run)
    end
  end

  defp run_pass(graph, context, concurrency) do
    Logger.info("Counting citable subjects...")
    total = count_citable_subjects()

    Logger.info("Found #{total} citable subjects")

    started_ms = System.monotonic_time(:millisecond)

    {_processed, main_written} =
      citable_subjects()
      |> Stream.with_index(1)
      |> Stream.chunk_every(Application.fetch_env!(:biblio_materializer, :read_batch_size))
      |> Stream.flat_map(&populate_batch(&1, total, graph, context, concurrency))
      |> Writer.batches()
      |> Task.async_stream(
        fn batch -> {length(batch), Writer.write_statements(graph, Enum.concat(batch))} end,
        max_concurrency: min(concurrency, @write_concurrency),
        ordered: false,
        timeout: :infinity
      )
      |> Enum.reduce({0, 0}, fn {:ok, {subjects, written}}, {processed, total_written} ->
        acc = {processed + subjects, total_written + written}
        log_progress(processed, acc, total, started_ms, context.queue)
        acc
      end)

    case DataCiteQueue.pending(context.queue) do
      0 ->
        :ok

      pending ->
        Logger.info(
          "Main pass done, wrote #{main_written} triples; " <>
            "waiting for the DataCite queue (#{pending} subjects pending)"
        )
    end

    {queue_written, queue_failed} = DataCiteQueue.drain(context.queue)
    written = main_written + queue_written

    skipped =
      if queue_failed == 0,
        do: "",
        else: " (#{queue_failed} subjects skipped on DataCite failures)"

    Logger.info(
      "Citation population finished, wrote #{written} triples " <>
        "(#{queue_written} via the DataCite queue)#{skipped}"
    )

    written
  end

  defp citable_subjects_query do
    classes = [Vocab.data_object_class(), Vocab.doc_object_class(), Vocab.collection_class()]
    values = Enum.map_join(classes, " ", &"<#{&1}>")

    """
    SELECT DISTINCT ?s ?class WHERE {
      VALUES ?class { #{values} }
      ?s a ?class .
    }
    """
  end

  defp count_citable_subjects do
    [row] = Sparql.select("SELECT (COUNT(*) AS ?count) WHERE { { #{citable_subjects_query()} } }")
    String.to_integer(row["count"]["value"])
  end

  # Lazy, cursor-paged stream over all citable subjects — nothing is
  # fetched up front, and no server row cap can truncate the scan.
  defp citable_subjects do
    citable_subjects_query()
    |> Sparql.select_stream("?s")
    |> Stream.map(fn row -> {row["s"]["value"], row["class"]["value"]} end)
  end

  # Processes one batch of subjects: their per-subject fields are read up
  # front, in one query per field group for the whole batch, instead of once
  # per subject. Returns a statement list per subject of the batch — empty for
  # the ones that yielded nothing — so that the batch stays accounted for
  # downstream.
  #
  # Already-materialized subjects are dropped before the prefetch: on a
  # resumed run they are the bulk of the batch, and reading fields the run
  # will not use would defeat the point.
  defp populate_batch(batch, total, graph, context, concurrency) do
    materialized =
      batch |> Enum.map(fn {{uri, _class}, _idx} -> uri end) |> Subject.materialized(graph)

    {done, todo} =
      Enum.split_with(batch, fn {{uri, _class}, _idx} -> MapSet.member?(materialized, uri) end)

    subjects = Enum.map(todo, fn {subject, _idx} -> subject end)
    Subject.load(context.cache, subjects, graph)

    try do
      populated =
        todo
        |> Task.async_stream(
          fn {{uri, class}, idx} -> populate("#{idx}/#{total}", uri, class, graph, context) end,
          max_concurrency: concurrency,
          ordered: false,
          timeout: :infinity
        )
        |> Enum.map(fn {:ok, statements} -> statements end)

      Enum.map(done, fn _subject -> [] end) ++ populated
    after
      Subject.forget(context.cache, subjects)
    end
  end

  # Returns the subject's triples as INSERT DATA statements for the batching
  # writer, rather than writing them itself.
  defp populate(tag, uri, class, graph, context) do
    # Reads may still fail after the SPARQL client's retries (e.g. a longer
    # Virtuoso overload); that skips the subject (caught up on the next run)
    # instead of crashing the whole run. Write failures stay fatal.
    result =
      try do
        References.build(uri, class, graph, context)
      rescue
        e -> {:error, Exception.format(:error, e, __STACKTRACE__) |> String.slice(0, 500)}
      end

    case result do
      {:ok, refs} ->
        statements(Writer.all_triples(uri, refs))

      {:deferred, job} ->
        DataCiteQueue.push(context.queue, Map.merge(job, %{uri: uri, tag: tag, graph: graph}))
        Logger.debug("[#{tag}] Deferred #{uri} to the DataCite queue")
        statements(Writer.licence_triple(uri, job.refs_base))

      :none ->
        Logger.debug("[#{tag}] No citation triples produced for #{uri}")
        []

      {:error, reason} ->
        Logger.warning("[#{tag}] Skipping #{uri}: #{reason}")
        []
    end
  end

  defp statements(triples), do: Enum.map(triples, &Writer.statement/1)

  @log_every 500

  defp log_progress(before, {processed, written}, total, started_ms, queue) do
    if div(processed, @log_every) > div(before, @log_every) or processed == total do
      elapsed_s = (System.monotonic_time(:millisecond) - started_ms) / 1000
      rate = if elapsed_s > 0, do: processed / elapsed_s, else: 0.0
      eta_s = if rate > 0, do: round((total - processed) / rate), else: 0
      eta_min = div(eta_s, 60)
      eta_remaining_s = rem(eta_s, 60)

      Logger.info(
        "Progress: #{processed}/#{total} subjects " <>
          "(#{Float.round(100.0 * processed / total, 1)}%), #{written} triples written, " <>
          "#{Float.round(rate, 1)} subj/s, ETA #{eta_min} min #{eta_remaining_s} s, " <>
          "#{DataCiteQueue.pending(queue)} pending in the DataCite queue"
      )
    end
  end
end
