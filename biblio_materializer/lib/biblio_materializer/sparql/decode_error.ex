defmodule BiblioMaterializer.Sparql.DecodeError do
  @moduledoc """
  Raised when an endpoint answers 200 but its body is not valid SPARQL JSON
  results.

  Separate from a plain query failure so that callers can retry a truncated
  response or narrow a query whose streamed computation failed midway.
  """

  defexception [:endpoint, :reason, :body]

  @window 200

  @impl Exception
  def message(%__MODULE__{endpoint: endpoint, reason: reason, body: body}) do
    "SPARQL query to #{endpoint} returned undecodable JSON " <>
      "(#{byte_size(body)} bytes, #{inspect(reason)}) near: " <>
      inspect(around(body, offset(reason)))
  end

  defp offset({:invalid_byte, offset, _byte}), do: offset
  defp offset({:unexpected_end, offset}), do: offset
  defp offset(_other), do: 0

  defp around(body, offset) do
    binary_slice(body, max(offset - @window, 0), 2 * @window)
  end
end
