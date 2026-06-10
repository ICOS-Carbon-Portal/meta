defmodule CitationPopulator do
  @moduledoc """
  Populates the derived-citations named graph in Virtuoso with the citation
  triples for all citable subjects (data objects, document objects and
  collections) — the same three triples the Scala citations service
  materializes:

    * `cpmeta:hasBiblioInfo` — the subject's `References` as compact JSON
    * `cpmeta:hasCitationString` — the plain-text citation
    * `dcterms:license` — the licence IRI

  Deliberately unoptimized: subjects are processed sequentially, one raw
  SPARQL query/update over HTTP per step, and the citation data itself comes
  from the citations service HTTP API, which computes it freshly.

  Only subjects with no triples in the derived graph are populated;
  already-materialized subjects are left untouched (even if stale).
  """

  require Logger

  alias CitationPopulator.{MetaService, Sparql}

  @cpmeta "http://meta.icos-cp.eu/ontologies/cpmeta/"
  @citable_classes [@cpmeta <> "DataObject", @cpmeta <> "DocumentObject", @cpmeta <> "Collection"]
  @has_citation_string @cpmeta <> "hasCitationString"
  @has_biblio_info @cpmeta <> "hasBiblioInfo"
  @dcterms_license "http://purl.org/dc/terms/license"

  # The citations service serves these placeholders instead of a DOI citation
  # while the DataCite lookup is pending or failed; they must not end up in
  # the triplestore, so such subjects are retried and eventually skipped.
  @pending_prefixes ["Fetching...", "Error fetching DOI citation"]
  @pending_retries 10
  @pending_retry_delay_ms 5_000

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
    values = Enum.map_join(@citable_classes, " ", &"<#{&1}>")

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
    case fetch_references(tag, uri, class, @pending_retries) do
      {:ok, refs} ->
        triples = triples_for(uri, refs)
        Sparql.update(insert_data(graph, triples))
        Logger.info("[#{tag}] Wrote #{length(triples)} triples for #{uri}")
        length(triples)

      :skip ->
        0
    end
  end

  defp fetch_references(tag, uri, class, retries_left) do
    case MetaService.fetch_references(uri, class) do
      {:ok, refs} ->
        cond do
          not citation_pending?(refs) ->
            {:ok, refs}

          retries_left > 0 ->
            Logger.info(
              "[#{tag}] DOI citation for #{uri} not ready yet, " <>
                "retrying in #{div(@pending_retry_delay_ms, 1000)} s"
            )

            Process.sleep(@pending_retry_delay_ms)
            fetch_references(tag, uri, class, retries_left - 1)

          true ->
            Logger.warning("[#{tag}] Skipping #{uri}: DOI citation still not ready")
            :skip
        end

      :not_found ->
        Logger.warning("[#{tag}] Skipping #{uri}: not found in the citations service")
        :skip

      {:error, reason} ->
        Logger.warning("[#{tag}] Skipping #{uri}: #{reason}")
        :skip
    end
  end

  defp citation_pending?(refs) do
    refs
    |> Map.take(["citationString", "citationBibTex", "citationRis"])
    |> Map.values()
    |> Enum.any?(fn cit ->
      is_binary(cit) and String.starts_with?(cit, @pending_prefixes)
    end)
  end

  defp triples_for(uri, refs) do
    biblio = [{uri, @has_biblio_info, literal(JSON.encode!(refs))}]

    citation =
      case refs["citationString"] do
        cit when is_binary(cit) -> [{uri, @has_citation_string, literal(cit)}]
        _ -> []
      end

    licence =
      case refs["licence"] do
        %{"url" => url} when is_binary(url) -> [{uri, @dcterms_license, "<#{url}>"}]
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
