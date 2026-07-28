defmodule CitationPopulator.Columns do
  @moduledoc """
  Resolves the actual columns/variables of a station time-series object into
  `%{label, has_unit}` entries — port of meta's VarMetaLookup as used by the
  citation logic (the atm-GHG title variable list and the atmosphere-theme
  attribution species filter).
  """

  alias CitationPopulator.{Cache, Rdf}

  @doc "nil when nothing can be resolved (matching `columns = None` in Scala)."
  def for_object(_cache, nil = _dataset_uri, _actual_names_json), do: nil

  def for_object(cache, dataset_uri, actual_names_json) do
    defs = column_defs(cache, dataset_uri)

    columns =
      case parse_actual(actual_names_json) do
        nil ->
          # No actual column names recorded: all plain mandatory columns.
          defs
          |> Enum.filter(&(not &1.regex and not &1.optional))
          |> Enum.map(&%{label: &1.title, has_unit: &1.has_unit})

        names ->
          Enum.flat_map(names, &lookup(&1, defs))
      end

    if columns == [], do: nil, else: columns
  end

  defp lookup(name, defs) do
    case Enum.find(defs, &(&1.title == name)) do
      %{} = exact ->
        [%{label: exact.title, has_unit: exact.has_unit}]

      nil ->
        defs
        |> Enum.filter(& &1.regex)
        |> Enum.sort_by(& &1.optional)
        |> Enum.find(&full_match?(&1.title, name))
        |> case do
          nil -> []
          regex_def -> [%{label: name, has_unit: regex_def.has_unit}]
        end
    end
  end

  # Java's String.matches anchors the pattern to the whole string.
  defp full_match?(pattern, name) do
    case Regex.compile("^(?:" <> pattern <> ")$") do
      {:ok, re} -> Regex.match?(re, name)
      _ -> false
    end
  end

  # A dataset's column/variable definitions are shared by every object of
  # that spec, so resolve them once per dataset per run. Only the per-object
  # `actual_names` filtering above stays live.
  defp column_defs(cache, dataset_uri) do
    Cache.fetch(cache, {:columns, dataset_uri}, fn -> column_defs_uncached(dataset_uri) end)
  end

  defp column_defs_uncached(dataset_uri) do
    Rdf.select("""
    SELECT DISTINCT ?title ?unit ?regex ?optional WHERE {
      {
        <#{dataset_uri}> cpmeta:hasColumn ?col .
        ?col cpmeta:hasColumnTitle ?title .
        OPTIONAL { ?col cpmeta:hasValueType ?vt . ?vt cpmeta:hasUnit ?unit }
        OPTIONAL { ?col cpmeta:isRegexColumn ?regex }
        OPTIONAL { ?col cpmeta:isOptionalColumn ?optional }
      } UNION {
        <#{dataset_uri}> cpmeta:hasVariable ?v .
        ?v cpmeta:hasVariableTitle ?title .
        OPTIONAL { ?v cpmeta:hasValueType ?vt . ?vt cpmeta:hasUnit ?unit }
        OPTIONAL { ?v cpmeta:isRegexVariable ?regex }
        OPTIONAL { ?v cpmeta:isOptionalVariable ?optional }
      }
    }
    """)
    |> Enum.map(fn r ->
      %{
        title: Rdf.val(r, "title"),
        has_unit: Rdf.val(r, "unit") != nil,
        regex: Rdf.parse_bool(Rdf.val(r, "regex")),
        optional: Rdf.parse_bool(Rdf.val(r, "optional"))
      }
    end)
  end

  defp parse_actual(nil), do: nil

  defp parse_actual(json) do
    case JSON.decode(json) do
      {:ok, names} when is_list(names) -> Enum.filter(names, &is_binary/1)
      _ -> nil
    end
  end
end
