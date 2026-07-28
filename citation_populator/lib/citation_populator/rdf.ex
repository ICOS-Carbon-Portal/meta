defmodule CitationPopulator.Rdf do
  @moduledoc """
  Low-level triplestore reading with raw SPARQL: shared prefixes, single-row
  reads, typed literal parsing.
  """

  alias CitationPopulator.Sparql

  @prefixes """
  PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
  PREFIX prov: <http://www.w3.org/ns/prov#>
  PREFIX dcterms: <http://purl.org/dc/terms/>
  PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
  PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
  """

  def select(query), do: Sparql.select(@prefixes <> query)

  def select_one(query), do: query |> select() |> List.first()

  @doc "The bound value of a variable in a result row, or nil."
  def val(nil, _name), do: nil
  def val(row, name), do: get_in(row, [name, "value"])

  @doc "All values of one variable across the result rows."
  def values(query, name), do: query |> select() |> Enum.map(&val(&1, name)) |> Enum.uniq()

  def parse_datetime(nil), do: nil

  def parse_datetime(s) do
    case DateTime.from_iso8601(s) do
      {:ok, dt, _offset} ->
        dt

      {:error, :missing_offset} ->
        case DateTime.from_iso8601(s <> "Z") do
          {:ok, dt, _offset} -> dt
          _ -> nil
        end

      _ ->
        nil
    end
  end

  def parse_date(nil), do: nil

  def parse_date(s) do
    case Date.from_iso8601(String.slice(s, 0, 10)) do
      {:ok, d} -> d
      _ -> nil
    end
  end

  def parse_float(s), do: parse_number(s, &Float.parse/1)

  def parse_int(s), do: parse_number(s, &Integer.parse/1)

  defp parse_number(nil, _parser), do: nil

  defp parse_number(value, parser) do
    case parser.(value) do
      {number, _rest} -> number
      :error -> nil
    end
  end

  def parse_bool("true"), do: true
  def parse_bool(_), do: false
end
