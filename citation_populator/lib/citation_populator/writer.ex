defmodule CitationPopulator.Writer do
  @moduledoc "Builds the materialized triples and writes them with SPARQL INSERT DATA."

  alias CitationPopulator.{Sparql, Vocab}

  @doc "Inserts the triples into the graph; returns how many were written."
  def write(_graph, []), do: 0

  def write(graph, triples) do
    Sparql.update(insert_data(graph, triples))
    length(triples)
  end

  def all_triples(uri, refs) do
    biblio_triple(uri, refs) ++ citation_triple(uri, refs) ++ licence_triple(uri, refs)
  end

  def biblio_triple(uri, refs), do: [{uri, Vocab.has_biblio_info(), literal(JSON.encode!(refs))}]

  def citation_triple(uri, refs) do
    case refs["citationString"] do
      cit when is_binary(cit) -> [{uri, Vocab.has_citation_string(), literal(cit)}]
      _ -> []
    end
  end

  def licence_triple(uri, refs) do
    case refs["licence"] do
      %{"url" => url} when is_binary(url) -> [{uri, Vocab.dcterms_license(), "<#{url}>"}]
      _ -> []
    end
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
