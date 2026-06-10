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

  Deliberately unoptimized: subjects are processed sequentially with one
  SPARQL query/update per step and no caching, batching or concurrency.

  Only subjects with no triples in the derived graph are populated;
  already-materialized subjects are left untouched (even if stale).
  """

  require Logger

  alias CitationPopulator.{References, Sparql, Vocab}

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

    written =
      todo
      |> Enum.with_index(1)
      |> Enum.reduce(0, fn {{uri, class}, idx}, acc ->
        acc + populate("#{idx}/#{total}", uri, class, graph)
      end)

    Logger.info("Citation population finished, wrote #{written} triples")
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
    |> Sparql.select_paged("?s")
    |> Enum.map(fn row -> {row["s"]["value"], row["class"]["value"]} end)
  end

  defp list_materialized_subjects(graph) do
    """
    SELECT DISTINCT ?s WHERE {
      GRAPH <#{graph}> { ?s ?p ?o }
    }
    """
    |> Sparql.select_paged("?s")
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
        write(tag, uri, graph, triples_for(uri, refs))

      {:citation_only, citation} ->
        write(tag, uri, graph, [{uri, Vocab.has_citation_string(), literal(citation)}])

      :none ->
        Logger.info("[#{tag}] No citation triples produced for #{uri}")
        0

      {:error, reason} ->
        Logger.warning("[#{tag}] Skipping #{uri}: #{reason}")
        0
    end
  end

  defp write(tag, uri, graph, triples) do
    Sparql.update(insert_data(graph, triples))
    Logger.info("[#{tag}] Wrote #{length(triples)} triples for #{uri}")
    length(triples)
  end

  defp triples_for(uri, refs) do
    biblio = [{uri, Vocab.has_biblio_info(), literal(JSON.encode!(refs))}]

    citation =
      case refs["citationString"] do
        cit when is_binary(cit) -> [{uri, Vocab.has_citation_string(), literal(cit)}]
        _ -> []
      end

    licence =
      case refs["licence"] do
        %{"url" => url} when is_binary(url) -> [{uri, Vocab.dcterms_license(), "<#{url}>"}]
        _ -> []
      end

    biblio ++ citation ++ licence
  end

  defp insert_data(graph, triples) do
    body = Enum.map_join(triples, "\n", fn {s, p, o} -> "  <#{s}> <#{p}> #{o} ." end)
    "INSERT DATA { GRAPH <#{graph}> {\n#{body}\n} }"
  end

  defp literal(string) do
    escaped =
      string
      |> String.replace("\\", "\\\\")
      |> String.replace("\"", "\\\"")
      |> String.replace("\n", "\\n")
      |> String.replace("\r", "\\r")
      |> String.replace("\t", "\\t")

    "\"#{escaped}\""
  end
end
