defmodule CitationPopulator.Http do
  @moduledoc """
  Thin wrapper around OTP's `:httpc`: binary bodies, string headers,
  lowercased response header names.
  """

  @http_options [timeout: 600_000, connect_timeout: 10_000]

  def get(url, headers \\ []) do
    request(:get, {String.to_charlist(url), to_httpc_headers(headers)})
  end

  def post_form(url, params, headers \\ []) do
    body = URI.encode_query(params)

    request(
      :post,
      {String.to_charlist(url), to_httpc_headers(headers), ~c"application/x-www-form-urlencoded",
       body}
    )
  end

  defp request(method, req) do
    case :httpc.request(method, req, @http_options, body_format: :binary) do
      {:ok, {{_http_version, status, _reason}, resp_headers, body}} ->
        headers =
          Enum.map(resp_headers, fn {name, value} ->
            {name |> to_string() |> String.downcase(), to_string(value)}
          end)

        {:ok, status, headers, body}

      {:error, reason} ->
        {:error, reason}
    end
  end

  defp to_httpc_headers(headers) do
    Enum.map(headers, fn {name, value} ->
      {String.to_charlist(name), String.to_charlist(value)}
    end)
  end
end
