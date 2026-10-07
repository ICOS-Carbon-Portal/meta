defmodule LandingBench do
  @moduledoc """
  Benchmarks data object landing pages of an ICOS/SITES data portal.

  1. Fetches the portal main page from the given data host and reads its ENVRI config.
  2. Runs the portal's default search query (newest objects first) against the meta host's SPARQL endpoint.
  3. Fetches the landing page of each returned data object in turn, timing each request
     and pausing between requests, until `max` landing pages have been fetched or the
     result list is exhausted.
  """

  alias LandingBench.{Http, Portal, Sparql, Stats}

  # Same page size as the portal (`stepsize` in config.ts)
  @page_size 20

  @type opts :: [
          host: String.t(),
          max: pos_integer(),
          delay_ms: non_neg_integer(),
          jitter_ms: non_neg_integer(),
          insecure: boolean(),
          timeout_ms: pos_integer(),
          csv: String.t() | nil
        ]

  @spec run(opts()) :: :ok | {:error, term()}
  def run(opts) do
    req_opts = Http.base_opts(opts)

    with {:ok, portal} <- Portal.fetch(base_url(opts[:host]), req_opts) do
      IO.puts(
        "Portal page #{portal.portal_url} fetched in #{fmt_ms(portal.fetch_ms)} " <>
          "(envri #{portal.envri}, meta host #{portal.meta_host})"
      )

      meta_base = "https://" <> portal.meta_host

      results =
        objects_stream(meta_base <> "/sparql", portal.envri, req_opts)
        |> Stream.take(opts[:max])
        |> Stream.with_index(1)
        |> Stream.map(fn {obj, i} ->
          if i > 1, do: pause(opts[:delay_ms], opts[:jitter_ms])
          fetch_landing_page(obj, meta_base, i, req_opts)
        end)
        |> Enum.to_list()

      Stats.print_summary(results)
      if path = opts[:csv], do: Stats.write_csv(results, path)
      :ok
    end
  rescue
    err in RuntimeError -> {:error, err}
  end

  defp base_url("http://" <> _ = url), do: url
  defp base_url("https://" <> _ = url), do: url
  defp base_url(host), do: "https://" <> host

  # Lazily pages through the default search result list, so that only as many
  # SPARQL queries are made as needed to reach `max` objects.
  defp objects_stream(endpoint, envri, req_opts) do
    Stream.resource(
      fn -> 0 end,
      fn
        :done ->
          {:halt, :done}

        offset ->
          case Sparql.list_objects(endpoint, envri, offset, @page_size, req_opts) do
            {:ok, rows, ms} ->
              IO.puts("SPARQL page at offset #{offset}: #{length(rows)} objects in #{fmt_ms(ms)}")
              next = if length(rows) < @page_size, do: :done, else: offset + @page_size
              {rows, next}

            {:error, err} when is_binary(err) ->
              raise "SPARQL listing failed: #{err}"

            {:error, err} ->
              raise "SPARQL listing failed: #{Exception.message(err)}"
          end
      end,
      fn _ -> :ok end
    )
  end

  defp fetch_landing_page(obj, meta_base, i, req_opts) do
    url = landing_page_url(obj.uri, meta_base)
    opts = Keyword.put(req_opts, :headers, [{"accept", "text/html"}])

    result =
      case Http.timed_get(url, opts) do
        {:ok, resp, ms} ->
          %{url: url, status: resp.status, ms: ms, bytes: byte_size(resp.body), error: nil}

        {:error, err} ->
          %{url: url, status: nil, ms: nil, bytes: 0, error: Exception.message(err)}
      end

    print_result(i, obj, result)
    result
  end

  # Object URIs are canonical (e.g. https://meta.icos-cp.eu/objects/<hash>); point
  # them at the meta host the portal is configured with, so local setups work too.
  defp landing_page_url(uri, meta_base) do
    hash = uri |> URI.parse() |> Map.fetch!(:path) |> Path.basename()
    meta_base <> "/objects/" <> hash
  end

  defp pause(delay_ms, jitter_ms) do
    jitter = if jitter_ms > 0, do: :rand.uniform(jitter_ms + 1) - 1, else: 0
    Process.sleep(delay_ms + jitter)
  end

  defp print_result(i, obj, %{error: nil} = r) do
    IO.puts(
      "#{pad(i)} #{r.status} #{String.pad_leading(fmt_ms(r.ms), 11)} " <>
        "#{String.pad_leading(Integer.to_string(r.bytes), 8)} B  #{r.url}  (#{obj.file_name})"
    )
  end

  defp print_result(i, _obj, r), do: IO.puts("#{pad(i)} ERR #{r.url}: #{r.error}")

  defp pad(i), do: String.pad_leading("##{i}", 5)

  def fmt_ms(ms), do: :erlang.float_to_binary(ms, decimals: 1) <> " ms"
end
