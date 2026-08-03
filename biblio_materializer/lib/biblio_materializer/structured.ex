defmodule BiblioMaterializer.Structured do
  @moduledoc """
  Port of StructuredCitations: BibTeX and RIS strings assembled from the
  object's own metadata, used when the object has no DOI (DataCite provides
  them otherwise).
  """

  alias BiblioMaterializer.Agent

  @newline "\r\n"

  @doc """
  Input map fields: pid_url, file_name, authors (list of agent JSON or nil),
  title, temp_cov, year, note, keywords (list or nil), publisher, licence_url,
  doi_raw, pid, hash_id.
  """
  def to_bibtex(s) do
    key = s.pid_url || s.file_name || s.hash_id

    tags = [
      {"author", s.authors && Enum.map_join(s.authors, " and ", &Agent.format_full/1)},
      {"title", title_with_temp_cov(s)},
      {"year", s.year},
      {"note", s.note},
      {"keywords", keywords_tag(s.keywords)},
      {"url", s.pid_url},
      {"publisher", s.publisher},
      {"copyright", s.licence_url},
      {"doi", s.doi_raw},
      {"pid", s.pid}
    ]

    body =
      tags
      |> Enum.filter(fn {_k, v} -> v end)
      |> Enum.map_join(",#{@newline}", fn {k, v} -> "  #{k}={#{v}}" end)

    "@misc{#{key},#{@newline}#{body}#{@newline}}"
  end

  def to_ris(s) do
    tags =
      [
        {"TY", "DATA"},
        {"T1", title_with_temp_cov(s)},
        {"ID", s.pid},
        {"DO", s.doi_raw},
        {"PY", s.year},
        {"AB", s.note},
        {"UR", s.pid_url},
        {"PB", s.publisher}
      ] ++
        Enum.map(s.authors || [], &{"AU", Agent.format_full(&1)}) ++
        Enum.map(s.keywords || [], &{"KW", &1}) ++
        [{"ER", ""}]

    tags
    |> Enum.filter(fn {_k, v} -> v end)
    |> Enum.map_join(@newline, fn {k, v} -> "#{k} - #{v}" end)
  end

  defp title_with_temp_cov(s) do
    if s.title && s.temp_cov, do: "#{s.title}, #{s.temp_cov}"
  end

  defp keywords_tag(nil), do: nil
  defp keywords_tag([]), do: nil
  defp keywords_tag(keywords), do: Enum.join(keywords, ", ")
end
