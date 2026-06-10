defmodule CitationPopulator.Util do
  @moduledoc "Small shared helpers."

  @doc "Puts a key only when the value is not nil (spray-json omits None fields)."
  def put_opt(map, _key, nil), do: map
  def put_opt(map, key, value), do: Map.put(map, key, value)

  @doc "nil for nil/empty lists, the list otherwise."
  def non_empty(nil), do: nil
  def non_empty([]), do: nil
  def non_empty(list), do: list

  @doc "Port of meta's parseCommaSepList: split, trim, drop empties. nil stays nil."
  def parse_comma_sep(nil), do: nil

  def parse_comma_sep(s) do
    s |> String.split(",") |> Enum.map(&String.trim/1) |> Enum.reject(&(&1 == ""))
  end

  def last_segment(uri) do
    uri |> String.trim_trailing("/") |> String.split("/") |> List.last()
  end
end
