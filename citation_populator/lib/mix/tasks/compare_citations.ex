defmodule Mix.Tasks.CompareCitations do
  @moduledoc """
  Compares the materialized citation triples between the local Virtuoso
  instance (`VIRTUOSO_HOST`) and another SPARQL endpoint — e.g. to check the
  Elixir populator's output against the Scala service's, or a production
  triplestore against a local one.

  Streams `cpmeta:hasCitationString`, `cpmeta:hasBiblioInfo` and
  `dcterms:license` triples (the ones `CitationPopulator.Writer` produces)
  out of a named graph on each side with the same cursor-paged SELECT the
  populator itself uses, groups them by subject, and reports how many
  subjects match and how many mismatch — printing every mismatch with both
  sides' values.

  ## Usage

      mix compare_citations --endpoint https://meta.icos-cp.eu/sparql
      mix compare_citations --endpoint http://other-host:8890/sparql \\
        --remote-graph http://meta.icos-cp.eu/resources/citations/

  ## Options

    * `--endpoint` (required) — the other SPARQL endpoint's query URL.
    * `--graph` — the named graph to read on both sides; defaults to the
      local `DERIVED_CITATIONS_GRAPH` configuration.
    * `--remote-graph` — overrides the graph read on the other endpoint,
      when it differs from the local one.
  """
  @shortdoc "Diffs materialized citation triples against another SPARQL endpoint"

  use Mix.Task

  alias CitationPopulator.{Sparql, Vocab}

  @impl Mix.Task
  def run(argv) do
    {opts, _rest} =
      OptionParser.parse!(argv,
        strict: [endpoint: :string, graph: :string, remote_graph: :string]
      )

    endpoint = opts[:endpoint] || Mix.raise("--endpoint <sparql endpoint URL> is required")

    # `loadconfig` alone only merges config/config.exs — config/runtime.exs
    # (where VIRTUOSO_HOST etc. are read from the environment) is otherwise
    # only ever read by `mix run` and releases, so read it the same way here.
    Mix.Task.run("compile")
    Mix.Task.run("loadconfig")

    "config/runtime.exs"
    |> Config.Reader.read!(env: Mix.env(), target: Mix.target())
    |> Application.put_all_env()

    # Loading the app config would otherwise kick off a full population pass
    # on start (CitationPopulator.Application's default behavior) — this task
    # only ever reads.
    Application.put_env(:citation_populator, :run_on_start, false)
    {:ok, _} = Application.ensure_all_started(:citation_populator)

    local_graph =
      opts[:graph] || Application.fetch_env!(:citation_populator, :derived_citations_graph)

    remote_graph = opts[:remote_graph] || local_graph
    local_host = Application.fetch_env!(:citation_populator, :virtuoso_host)

    Mix.shell().info(
      "Streaming local triples from #{local_host}/sparql (graph #{local_graph})..."
    )

    local = collect_subjects(local_graph)

    Mix.shell().info("Streaming remote triples from #{endpoint} (graph #{remote_graph})...")
    remote = collect_subjects(remote_graph, endpoint)

    report(local, remote)
  end

  defp predicates,
    do: [Vocab.has_citation_string(), Vocab.has_biblio_info(), Vocab.dcterms_license()]

  defp triples_query(graph) do
    values = Enum.map_join(predicates(), " ", &"<#{&1}>")

    """
    SELECT ?s ?p ?o WHERE {
      GRAPH <#{graph}> {
        ?s ?p ?o .
        VALUES ?p { #{values} }
      }
    }
    """
  end

  # Groups the streamed ?s ?p ?o rows by subject into %{predicate => object},
  # using the same cursor-paged SELECT the populator itself streams subjects
  # with, so no server result-set cap can truncate either side's scan.
  defp collect_subjects(graph, endpoint \\ nil) do
    stream =
      if endpoint,
        do: Sparql.select_stream(triples_query(graph), "?s ?p", endpoint),
        else: Sparql.select_stream(triples_query(graph), "?s ?p")

    Enum.reduce(stream, %{}, fn row, acc ->
      s = row["s"]["value"]
      p = row["p"]["value"]
      o = row["o"]["value"]
      Map.update(acc, s, %{p => o}, &Map.put(&1, p, o))
    end)
  end

  defp report(local, remote) do
    subjects = MapSet.new(Map.keys(local)) |> MapSet.union(MapSet.new(Map.keys(remote)))

    {matches, mismatches} =
      Enum.reduce(subjects, {0, []}, fn uri, {matches, mismatches} ->
        l = Map.get(local, uri, %{})
        r = Map.get(remote, uri, %{})

        if l == r,
          do: {matches + 1, mismatches},
          else: {matches, [{uri, l, r} | mismatches]}
      end)

    Mix.shell().info("")

    mismatches
    |> Enum.sort_by(fn {uri, _l, _r} -> uri end)
    |> Enum.each(&print_mismatch/1)

    Mix.shell().info("")
    Mix.shell().info("#{matches} matching subjects, #{length(mismatches)} mismatching")
  end

  defp print_mismatch({uri, local, remote}) do
    Mix.shell().info("MISMATCH #{uri}")
    Mix.shell().info("  local:  #{inspect(local)}")
    Mix.shell().info("  remote: #{inspect(remote)}")
  end
end
