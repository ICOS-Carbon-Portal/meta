defmodule CitationPopulator do
  @moduledoc """
  Populates the derived-citations named graph in Virtuoso with the citation
  triples for all citable subjects (data objects, document objects and
  collections) — a standalone replacement for the Scala citations service's
  materializer, computing everything itself from raw SPARQL over HTTP and
  the DataCite REST API:

    * `cpmeta:hasBiblioInfo` — the subject's `References` as compact JSON
    * `cpmeta:hasCitationString` — the plain-text citation
    * `dcterms:license` — the licence IRI

  Subjects are processed concurrently (MAX_CONCURRENCY, default 16). DOI
  subjects are not handled inline: the worker writes their DataCite-
  independent triples (the licence), hands the rest to the [`DataCiteQueue`]
  GenServer and moves on; the queue fetches from DataCite (globally
  throttled to 10 req/s) and writes the DataCite-dependent triples as the
  lookups complete. A run finishes when both the main pass and the queue
  are done.

  Only subjects with no triples in the derived graph are populated;
  already-materialized subjects are left untouched (even if stale). Note
  that this includes DOI subjects whose DataCite lookups failed in an
  earlier run: their licence triple makes them count as materialized.
  """

  require Logger

  alias CitationPopulator.{DataCiteQueue, References, Sparql, Vocab, Writer}

  def run do
    graph = Application.fetch_env!(:citation_populator, :derived_citations_graph)

    Logger.info("Listing citable subjects...")
    subjects = list_citable_subjects()
    materialized = list_materialized_subjects(graph)
    todo = Enum.reject(subjects, fn {uri, _class} -> MapSet.member?(materialized, uri) end)
    total = length(todo)

    Logger.info(
      "Found #{length(subjects)} citable subjects " <>
        "(#{MapSet.size(materialized)} already materialized, #{total} to populate)"
    )

    # 1: subjects processed, 2: triples written — for the periodic progress log
    progress = :atomics.new(2, [])
    started_ms = System.monotonic_time(:millisecond)

    main_written =
      todo
      |> Enum.with_index(1)
      |> Task.async_stream(
        fn {{uri, class}, idx} ->
          count = populate("#{idx}/#{total}", uri, class, graph)
          log_progress(progress, count, total, started_ms)
          count
        end,
        max_concurrency: Application.fetch_env!(:citation_populator, :max_concurrency),
        ordered: false,
        timeout: :infinity
      )
      |> Enum.reduce(0, fn {:ok, count}, acc -> acc + count end)

    case DataCiteQueue.pending() do
      0 ->
        :ok

      pending ->
        Logger.info(
          "Main pass done, wrote #{main_written} triples; " <>
            "waiting for the DataCite queue (#{pending} subjects pending)"
        )
    end

    {queue_written, queue_failed} = DataCiteQueue.drain()
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

  defp list_citable_subjects do
    classes = [Vocab.data_object_class(), Vocab.doc_object_class(), Vocab.collection_class()]
    values = Enum.map_join(classes, " ", &"<#{&1}>")

    """
    SELECT DISTINCT ?s ?class WHERE {
      VALUES ?class { #{values} }
      ?s a ?class .
    }
    """
    |> Sparql.select()
    |> Enum.map(fn row -> {row["s"]["value"], row["class"]["value"]} end)
  end

  defp list_materialized_subjects(graph) do
    """
    SELECT DISTINCT ?s WHERE {
      GRAPH <#{graph}> { ?s ?p ?o }
    }
    """
    |> Sparql.select()
    |> MapSet.new(fn row -> row["s"]["value"] end)
  end

  defp populate(tag, uri, class, graph) do
    result =
      try do
        References.build(uri, class, graph)
      rescue
        e -> {:error, Exception.format(:error, e, __STACKTRACE__) |> String.slice(0, 500)}
      end

    case result do
      {:ok, refs} ->
        count = Writer.write(graph, Writer.all_triples(uri, refs))
        Logger.debug("[#{tag}] Wrote #{count} triples for #{uri}")
        count

      {:deferred, job} ->
        count = Writer.write(graph, Writer.licence_triple(uri, job.refs_base))
        DataCiteQueue.push(Map.merge(job, %{uri: uri, tag: tag, graph: graph}))
        Logger.debug("[#{tag}] Deferred #{uri} to the DataCite queue")
        count

      :none ->
        Logger.debug("[#{tag}] No citation triples produced for #{uri}")
        0

      {:error, reason} ->
        Logger.warning("[#{tag}] Skipping #{uri}: #{reason}")
        0
    end
  end

  @log_every 500

  defp log_progress(progress, written_now, total, started_ms) do
    :atomics.add(progress, 2, written_now)
    processed = :atomics.add_get(progress, 1, 1)

    if rem(processed, @log_every) == 0 or processed == total do
      written = :atomics.get(progress, 2)
      elapsed_s = (System.monotonic_time(:millisecond) - started_ms) / 1000
      rate = if elapsed_s > 0, do: processed / elapsed_s, else: 0.0
      eta_s = if rate > 0, do: round((total - processed) / rate), else: 0

      Logger.info(
        "Progress: #{processed}/#{total} subjects " <>
          "(#{Float.round(100.0 * processed / total, 1)}%), #{written} triples written, " <>
          "#{Float.round(rate, 1)} subj/s, ETA #{eta_s} s, " <>
          "#{DataCiteQueue.pending()} pending in the DataCite queue"
      )
    end
  end
end
