defmodule BiblioMaterializer.SparqlDecodeErrorTest do
  use ExUnit.Case, async: true

  alias BiblioMaterializer.Sparql.DecodeError

  test "describes the endpoint, body size, decode reason, and nearby bytes" do
    body = String.duplicate("x", 300) <> "broken" <> String.duplicate("y", 300)

    message =
      Exception.message(%DecodeError{
        endpoint: "https://example.test/sparql",
        reason: {:invalid_byte, 300, ?b},
        body: body
      })

    assert message =~ "https://example.test/sparql"
    assert message =~ "#{byte_size(body)} bytes"
    assert message =~ "{:invalid_byte, 300, 98}"
    assert message =~ "broken"
  end

  test "handles an unexpected end near the start of a short body" do
    message =
      Exception.message(%DecodeError{
        endpoint: "https://example.test/sparql",
        reason: {:unexpected_end, 1},
        body: "{"
      })

    assert message =~ ~s(near: "{")
  end
end
