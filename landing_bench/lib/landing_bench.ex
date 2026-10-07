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
  """

  alias LandingBench.{Diff, Http, Portal, Sparql, Stats}

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
          diff: boolean()
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
      secondary_base = opts[:secondary] && base_url(opts[:secondary])

      if secondary_base, do: IO.puts("Secondary meta host #{secondary_base}")

      results =
        objects_stream(meta_base <> "/sparql", portal.envri, req_opts)
        |> Stream.take(opts[:max])
        |> Stream.with_index(1)
        |> Stream.map(fn {obj, i} ->
          if i > 1, do: pause(opts[:delay_ms], opts[:jitter_ms])
          fetch_object(obj, meta_base, secondary_base, i, req_opts, opts[:diff])
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

  defp fetch_object(obj, meta_base, nil, i, req_opts, _show_diff) do
    result = fetch_landing_page(landing_page_url(obj.uri, meta_base), req_opts)
    print_result(pad(i), result, "(#{obj.file_name})")
    Map.delete(result, :body)
  end

  defp fetch_object(obj, meta_base, secondary_base, i, req_opts, show_diff) do
    primary = fetch_landing_page(landing_page_url(obj.uri, meta_base), req_opts)
    print_result(pad(i), primary, "(#{obj.file_name})")

    secondary = fetch_landing_page(landing_page_url(obj.uri, secondary_base), req_opts)
    match = compare(primary, secondary)
    print_result(pad(""), secondary, "[#{match_label(match)}]")

    if show_diff and mismatch?(match),
      do: print_diff(primary, secondary)

    primary
    |> Map.delete(:body)
    |> Map.merge(%{secondary: Map.delete(secondary, :body), match: match})
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

  # If the bodies differ, they are compared again with known environment-specific
  # differences (see `LandingBench.Diff.ignore_known_differences/1`) removed.
  defp compare(%{error: nil} = a, %{error: nil} = b) do
    cond do
      a.status != b.status ->
        :status_differs

      a.body == b.body ->
        :identical

      true ->
        body_a = Diff.ignore_known_differences(a.body)
        body_b = Diff.ignore_known_differences(b.body)

        if body_a == body_b,
          do: :identical_ignoring_known,
          else: {:different, first_differing_line(body_a, body_b)}
    end
  end

  defp compare(_a, _b), do: :fetch_error

  defp first_differing_line(a, b) do
    Enum.zip(String.split(a, "\n"), String.split(b, "\n"))
    |> Enum.find_index(fn {la, lb} -> la != lb end)
    |> case do
      # one body is a prefix of the other, line-wise
      nil -> min(length(String.split(a, "\n")), length(String.split(b, "\n"))) + 1
      idx -> idx + 1
    end
  end

  defp mismatch?({:different, _line}), do: true
  defp mismatch?(:status_differs), do: true
  defp mismatch?(_match), do: false

  # Diffs the bodies with known differences removed, as in `compare/2`, so that
  # only the differences that caused the mismatch are shown.
  defp print_diff(primary, secondary) do
    IO.puts(IO.ANSI.format([:red, "     --- #{primary.url}"]))
    IO.puts(IO.ANSI.format([:green, "     +++ #{secondary.url}"]))

    Diff.unified(
      Diff.ignore_known_differences(primary.body),
      Diff.ignore_known_differences(secondary.body)
    )
    |> Enum.each(fn line -> IO.puts(IO.ANSI.format([diff_color(line), "     ", line])) end)
  end

  defp diff_color("@@" <> _), do: :cyan
  defp diff_color("-" <> _), do: :red
  defp diff_color("+" <> _), do: :green
  defp diff_color(_), do: :reset

  def match_label(:identical), do: "match"
  def match_label(:identical_ignoring_known), do: "match (ignoring known differences)"
  def match_label(:status_differs), do: "MISMATCH: HTTP status differs"
  def match_label(:fetch_error), do: "not compared: fetch error"
  def match_label({:different, line}), do: "MISMATCH: first difference at line #{line}"

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

  defp print_result(prefix, %{error: nil} = r, suffix) do
    IO.puts(
      "#{prefix} #{r.status} #{String.pad_leading(fmt_ms(r.ms), 11)} " <>
        "#{String.pad_leading(Integer.to_string(r.bytes), 8)} B  #{r.url}  #{suffix}"
    )
  end

  defp print_result(prefix, r, suffix),
    do: IO.puts("#{prefix} ERR #{r.url}: #{r.error}  #{suffix}")

  defp pad(""), do: String.duplicate(" ", 5)
  defp pad(i), do: String.pad_leading("##{i}", 5)

  def fmt_ms(ms), do: :erlang.float_to_binary(ms, decimals: 1) <> " ms"
end
