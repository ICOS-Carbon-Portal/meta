defmodule LandingBench do
  @moduledoc """
  Benchmarks data object landing pages of an ICOS/SITES data portal.

  1. Fetches the portal main page from the given data host and reads its ENVRI config.
  2. Runs the portal's default search query (newest objects first) against the meta host's SPARQL endpoint.
  3. Fetches the landing page of each returned data object in turn, timing each request
     and pausing between requests, until `max` landing pages have been fetched or the
     result list is exhausted.
  4. Optionally fetches each landing page from a secondary meta host as well, timing it
     and comparing the response with the one from the primary meta host, optionally
     printing a diff of the response bodies when they do not match.
  5. Optionally stores the run in a local directory (see `LandingBench.Store`), so that
     it can be viewed again later with `LandingBench.Viewer`.
  """

  alias LandingBench.{Diff, Http, Output, Portal, Sparql, Stats, Store}

  # Same page size as the portal (`stepsize` in config.ts)
  @page_size 20

  @type opts :: [
          host: String.t(),
          secondary: String.t() | nil,
          max: pos_integer(),
          delay_ms: non_neg_integer(),
          jitter_ms: non_neg_integer(),
          insecure: boolean(),
          timeout_ms: pos_integer(),
          csv: String.t() | nil,
          diff: boolean(),
          store: String.t() | nil
        ]

  @spec run(opts()) :: :ok | {:error, term()}
  def run(opts) do
    store = if dir = opts[:store], do: Store.open(dir, opts)

    # Every event is printed as it happens and, with `store`, recorded for later viewing
    emit = fn event ->
      Output.print(event, opts[:diff])
      if store, do: Store.record(store, event)
      event
    end

    case bench(opts, emit) do
      {:ok, results} = outcome ->
        Stats.print_summary(results)
        if path = opts[:csv], do: Stats.write_csv(results, path)
        if store, do: Store.finish(store, outcome)
        :ok

      {:error, _err} = outcome ->
        if store, do: Store.finish(store, outcome)
        outcome
    end
  end

  defp bench(opts, emit) do
    req_opts = Http.base_opts(opts)

    with {:ok, portal} <- Portal.fetch(base_url(opts[:host]), req_opts) do
      emit.(%{
        type: :portal,
        portal_url: portal.portal_url,
        fetch_ms: portal.fetch_ms,
        envri: portal.envri,
        meta_host: portal.meta_host
      })

      meta_base = "https://" <> portal.meta_host
      secondary_base = opts[:secondary] && base_url(opts[:secondary])

      if secondary_base, do: emit.(%{type: :secondary, base_url: secondary_base})

      results =
        objects_stream(meta_base <> "/sparql", portal.envri, req_opts, emit)
        |> Stream.take(opts[:max])
        |> Stream.with_index(1)
        |> Stream.map(fn {obj, i} ->
          if i > 1, do: pause(opts[:delay_ms], opts[:jitter_ms])

          fetch_object(obj, meta_base, secondary_base, i, req_opts)
          |> emit.()
          |> Stats.result()
        end)
        |> Enum.to_list()

      {:ok, results}
    end
  rescue
    err in RuntimeError -> {:error, err}
  end

  defp base_url("http://" <> _ = url), do: url
  defp base_url("https://" <> _ = url), do: url
  defp base_url(host), do: "https://" <> host

  # Lazily pages through the default search result list, so that only as many
  # SPARQL queries are made as needed to reach `max` objects.
  defp objects_stream(endpoint, envri, req_opts, emit) do
    Stream.resource(
      fn -> 0 end,
      fn
        :done ->
          {:halt, :done}

        offset ->
          case Sparql.list_objects(endpoint, envri, offset, @page_size, req_opts) do
            {:ok, rows, ms} ->
              emit.(%{type: :sparql_page, offset: offset, count: length(rows), ms: ms})
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

  defp fetch_object(obj, meta_base, secondary_base, i, req_opts) do
    primary = fetch_landing_page(landing_page_url(obj.uri, meta_base), req_opts)

    {secondary, match} =
      if secondary_base do
        secondary = fetch_landing_page(landing_page_url(obj.uri, secondary_base), req_opts)
        {secondary, Diff.compare(primary, secondary)}
      else
        {nil, nil}
      end

    %{
      type: :object,
      index: i,
      file_name: obj.file_name,
      uri: obj.uri,
      primary: primary,
      secondary: secondary,
      match: match
    }
  end

  defp fetch_landing_page(url, req_opts) do
    opts = Keyword.put(req_opts, :headers, [{"accept", "text/html"}])

    case Http.timed_get(url, opts) do
      {:ok, resp, ms} ->
        %{
          url: url,
          status: resp.status,
          ms: ms,
          bytes: byte_size(resp.body),
          body: resp.body,
          error: nil
        }

      {:error, err} ->
        %{url: url, status: nil, ms: nil, bytes: 0, body: nil, error: Exception.message(err)}
    end
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
end
