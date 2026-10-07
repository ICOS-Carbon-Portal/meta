defmodule LandingBench.Http do
  @moduledoc """
  Thin wrapper around Req that measures wall-clock time for a full request,
  including reading the complete response body.
  """

  @doc "Base Req options. `insecure: true` disables TLS certificate verification (for local dev hosts)."
  @spec base_opts(keyword()) :: keyword()
  def base_opts(opts) do
    transport_opts =
      if Keyword.get(opts, :insecure, false), do: [verify: :verify_none], else: []

    [
      retry: false,
      redirect: true,
      decode_body: false,
      receive_timeout: Keyword.get(opts, :timeout_ms, 60_000),
      connect_options: [transport_opts: transport_opts]
    ]
  end

  @spec timed_get(String.t(), keyword()) :: {:ok, Req.Response.t(), float()} | {:error, term()}
  def timed_get(url, req_opts) do
    timed(fn -> Req.get(url, req_opts) end)
  end

  @spec timed_post(String.t(), keyword()) :: {:ok, Req.Response.t(), float()} | {:error, term()}
  def timed_post(url, req_opts) do
    timed(fn -> Req.post(url, req_opts) end)
  end

  defp timed(fun) do
    start = System.monotonic_time()
    result = fun.()
    ms = System.convert_time_unit(System.monotonic_time() - start, :native, :microsecond) / 1000

    case result do
      {:ok, resp} -> {:ok, resp, ms}
      {:error, err} -> {:error, err}
    end
  end
end
