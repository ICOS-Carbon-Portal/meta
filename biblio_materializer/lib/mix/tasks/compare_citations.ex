defmodule Mix.Tasks.CompareCitations do
  @moduledoc """
  Compares the materialized citation triples between the local Virtuoso
  instance (`VIRTUOSO_HOST`) and another SPARQL endpoint — e.g. to check the
  Elixir populator's output against production.

  Streams `cpmeta:hasCitationString`, `cpmeta:hasBiblioInfo` and
  `dcterms:license` triples (the ones `BiblioMaterializer.Writer` produces)
  out of a local named graph with the same cursor-paged SELECT the populator
  itself uses, then looks the same three predicates up on the other endpoint
  for exactly those subjects and compares the two sides — printing every
  mismatch with both sides' values.

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

  # Local: the derived graph holds these as ordinary triples, so one unbound
  # cursor-paged stream (the same pattern the populator streams subjects
  # with) covers all of them — no server result-set cap can truncate it.
  defp collect_local_subjects(graph) do
    query = """
    SELECT ?s ?p ?o WHERE {
      GRAPH <#{graph}> {
        ?s ?p ?o .
        VALUES ?p { #{values(predicates())} }
      }
    }
    """

    query
    |> Sparql.select_stream("?s ?p")
    |> Enum.reduce(%{}, &add_triple/2)
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
    Map.update(acc, s, %{p => o}, &Map.put(&1, p, o))
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
  defp format_value(v), do: inspect(v)
end
