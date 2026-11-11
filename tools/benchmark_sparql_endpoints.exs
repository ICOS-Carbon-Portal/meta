#!/usr/bin/env elixir

Mix.install([
  {:req, "~> 0.5.0"},
  {:nimble_csv, "~> 1.2"}
])

NimbleCSV.define(QueryCSV, separator: ",", escape: "\"")

defmodule QueryBenchmark do
  @endpoints %{
    "prod" => "https://meta.icos-cp.eu/sparql",
    "fs4" => "https://meta.fs4vm.icos-cp.eu/sparql",
    "local" => "http://localhost:9094/sparql",
  }

  @default_query "select ?s ?p ?o where{ ?s ?p ?o } limit 1000000"

  def main(args) do
    {options, positional, invalid} =
      OptionParser.parse(args,
        strict: [
          query: :string,
          query_file: :string,
          runs: :integer,
          concurrency: :integer,
          close: :integer,
          close_after: :integer,
          close_jitter: :integer,
          help: :boolean
        ],
        aliases: [q: :query, f: :query_file, n: :runs, c: :concurrency, h: :help]
      )

    runs = Keyword.get(options, :runs, 10)
    concurrency = Keyword.get(options, :concurrency, 1)
    close = Keyword.get(options, :close, 0)
    close_after = Keyword.get(options, :close_after, 500)
    close_jitter = Keyword.get(options, :close_jitter, 1000)

    cond do
      options[:help] ->
        usage()

      invalid != [] or length(positional) != 2 or runs < 3 or concurrency < 1 or
        close < 0 or close > runs or close_after < 1 or close_jitter < 0 or
          (Keyword.has_key?(options, :query) and Keyword.has_key?(options, :query_file)) ->
        usage()
        System.halt(1)

      true ->
        query =
          cond do
            options[:query] -> options[:query]
            options[:query_file] -> File.read!(options[:query_file])
            true -> @default_query
          end

        benchmark(query, positional, runs, concurrency, close, close_after, close_jitter)
    end
  end

  defp usage do
    IO.puts("""
    Usage: elixir benchmark_query.exs ENDPOINT_1 ENDPOINT_2 [--query QUERY | --query-file FILE]
      [--runs N] [--concurrency N] [--close N] [--close-after MS] [--close-jitter MS]

    --query QUERY: SPARQL query text (short: -q).
    --query-file FILE: read the query from FILE (short: -f).
    Default query: #{@default_query}

    Endpoints: #{Enum.join(Enum.sort(Map.keys(@endpoints)), ", ")}, or HTTP(S) URLs.
    Bare host URLs get /sparql appended; URLs with a path are used as supplied.
    Default: 10 runs per endpoint; minimum: 3. Short option: -n N.
    Concurrency: up to N in-flight queries per endpoint (default: 1; short: -c N).
    Both endpoints run concurrently; --runs remains the total per endpoint.
    --close N: select N runs per endpoint to forcibly close (default: 0).
    Selected run numbers: 1 + floor(i * runs / N), for i = 0..N-1.
    --close-after MS: minimum delay before closing selected requests (default: 500).
    --close-jitter MS: add an independently random 0..MS delay per selected request
    (default: 1000; use 0 to disable). Requests still running at that delay are closed.
    Selected requests that finish earlier are counted as completed.
    Closed requests are excluded from timings, counts and response comparisons.
    All runs selected for closing are excluded from response comparisons, even if completed.
    Fewer than 3 completed requests means timing statistics are unavailable.
    Timing includes the complete HTTP response, excluding CSV parsing.
    Statistics exclude one fastest and one slowest run per endpoint.
    Counts include every completed run and count CSV data rows (excluding the header),
    not the numeric value returned by a SELECT COUNT(*) query.
    Response bodies are compared byte-for-byte across completed runs not selected for
    closing, including trimmed runs. Row order and whitespace differences count as differences.
    Each distinct response is diffed against the first eligible completed response.
    The diff ignores line order (order-only changes are reported as such) and prints at
    most 50 removed and 50 added lines per response.
    """)
  end

  defp endpoint_url(endpoint) do
    url = Map.get(@endpoints, endpoint, endpoint)
    uri = URI.parse(url)

    unless uri.scheme in ["http", "https"] and is_binary(uri.host) and uri.host != "" do
      raise ArgumentError, "invalid endpoint: #{endpoint} (use an alias or HTTP(S) URL)"
    end

    if uri.path in [nil, "", "/"] do
      URI.to_string(%{uri | path: "/sparql"})
    else
      url
    end
  end

  defp benchmark(query, endpoints, runs, concurrency, close, close_after, close_jitter) do
    targets = endpoints |> Enum.map(&{&1, endpoint_url(&1)}) |> Enum.with_index()

    IO.puts(
      "Running #{runs} times per endpoint with concurrency #{concurrency} per endpoint; " <>
        "dropping fastest and slowest for timing statistics."
    )

    selected =
      if close == 0,
        do: MapSet.new(),
        else: MapSet.new(0..(close - 1), &(1 + div(&1 * runs, close)))

    if close > 0 do
      IO.puts(
        "Closing runs #{selected |> Enum.sort() |> Enum.join(", ")} per endpoint " <>
          "after #{close_after} ms + random 0..#{close_jitter} ms if still running."
      )
    end

    {:ok, supervisor} = Task.Supervisor.start_link()

    attempts =
      try do
        targets
        |> async_map(2, fn {{name, url}, index} ->
          async_map(1..runs, concurrency, fn run ->
            deadline =
              if MapSet.member?(selected, run),
                do: close_after + :rand.uniform(close_jitter + 1) - 1,
                else: nil
            measure_query(query, name, url, index, run, runs, supervisor, deadline)
          end)
        end)
        |> List.flatten()
        |> Enum.sort_by(&{&1.run, &1.target})
      after
        Supervisor.stop(supervisor)
      end

    samples = Enum.reject(attempts, & &1.closed)

    Enum.each(targets, fn {{name, url}, index} ->
      results = Enum.filter(samples, &(&1.target == index))
      closed = Enum.count(attempts, &(&1.target == index and &1.closed))
      IO.puts("\n#{name} (#{url}) — #{length(results)} completed, #{closed} forcibly closed")
      report_times(results)

      IO.puts(
        "  Result counts (all completed runs): #{inspect(Enum.frequencies_by(results, & &1.count))} (count => runs)"
      )
    end)

    counts = samples |> Enum.map(& &1.count) |> Enum.uniq() |> Enum.sort()

    cond do
      counts == [] ->
        IO.puts("\nCounts differed: N/A (no completed responses)")

      length(counts) == 1 ->
        IO.puts("\nCounts differed: #{difference_status(false)} (#{hd(counts)} results in every completed run)")

      true ->
        IO.puts("\nCounts differed: #{difference_status(true)} (observed counts: #{Enum.join(counts, ", ")})")
    end

    comparison_samples = Enum.reject(samples, &MapSet.member?(selected, &1.run))
    report_response_differences(comparison_samples, targets)
  end

  defp report_times(results) when length(results) < 3 do
    IO.puts("  Timing statistics: N/A (need at least 3 completed runs to trim extremes)")
  end

  defp report_times(results) do
    times = results |> Enum.map(& &1.ms) |> Enum.sort() |> Enum.slice(1, length(results) - 2)

    median =
      (Enum.at(times, div(length(times) - 1, 2)) + Enum.at(times, div(length(times), 2))) / 2

    IO.puts("  #{length(times)} retained runs")
    IO.puts("  Median: #{format(median)} ms")
    IO.puts("  Average: #{format(Enum.sum(times) / length(times))} ms")
    IO.puts("  Min: #{format(hd(times))} ms")
    IO.puts("  Max: #{format(List.last(times))} ms")
  end

  defp measure_query(query, name, url, index, run, runs, supervisor, deadline) do
    {microseconds, result} = :timer.tc(fn -> request(query, url, supervisor, deadline) end)
    milliseconds = microseconds / 1000

    case result do
      :closed ->
        IO.puts("#{name} [#{run}/#{runs}]: forcibly closed after #{format(milliseconds)} ms")
        %{target: index, run: run, closed: true}

      {:ok, response} ->
        completed_sample(response, name, index, run, runs, milliseconds)

      {:error, exception} ->
        raise exception
    end
  end

  defp request(query, url, supervisor, deadline) do
    options = [
      form: [query: query],
      headers: [accept: "text/csv"],
      decode_body: false,
      retry: false,
      receive_timeout: 300_000
    ]

    if deadline do
      # HTTP/1 sockets belong to the requesting process. Killing that process
      # closes the socket, rather than leaving a pooled/multiplexed request alive.
      options = Keyword.put(options, :connect_options, protocols: [:http1])
      task = Task.Supervisor.async_nolink(supervisor, fn -> Req.post(url, options) end)

      case Task.yield(task, deadline) do
        {:ok, result} ->
          result

        {:exit, reason} ->
          raise "query task failed: #{inspect(reason)}"

        nil ->
          case Task.shutdown(task, :brutal_kill) do
            {:ok, result} -> result
            _ -> :closed
          end
      end
    else
      Req.post(url, options)
    end
  end

  defp completed_sample(response, name, index, run, runs, milliseconds) do
    unless response.status in 200..299 do
      raise "#{name}, run #{run}: HTTP #{response.status}: #{String.slice(response.body, 0, 300)}"
    end

    content_type = response |> Req.Response.get_header("content-type") |> Enum.join("; ")

    unless String.contains?(String.downcase(content_type), "text/csv") do
      raise "#{name}, run #{run}: expected text/csv, received #{inspect(content_type)}"
    end

    count = count_rows(response.body)
    IO.puts("#{name} [#{run}/#{runs}]: #{format(milliseconds)} ms, #{count} results")
    %{target: index, run: run, ms: milliseconds, count: count, body: response.body, closed: false}
  end

  defp async_map(items, concurrency, fun) do
    items
    |> Task.async_stream(
      fn item ->
        try do
          {:ok, fun.(item)}
        rescue
          exception -> {:error, exception}
        end
      end,
      max_concurrency: concurrency,
      timeout: :infinity
    )
    |> Enum.map(fn
      {:ok, {:ok, result}} -> result
      {:ok, {:error, exception}} -> raise exception
      {:exit, reason} -> raise "query task failed: #{inspect(reason)}"
    end)
  end

  defp report_response_differences([], _targets) do
    IO.puts("\nResponse bodies differed: N/A (no eligible completed responses)")
  end

  defp report_response_differences(samples, targets) do
    # Group by the complete body so identical counts cannot hide changed values.
    groups = Enum.group_by(samples, & &1.body)
    baseline = hd(samples)

    IO.puts("\nResponse bodies differed: #{difference_status(map_size(groups) > 1)}")

    Enum.each(targets, fn {{name, _url}, index} ->
      distinct =
        samples |> Enum.filter(&(&1.target == index)) |> Enum.uniq_by(& &1.body) |> length()

      IO.puts(
        "  Between runs on endpoint #{index + 1} (#{name}): #{if distinct == 0, do: "N/A (no eligible completed responses)", else: difference_status(distinct > 1)}"
      )
    end)

    # Multiple bodies imply a differing cross-endpoint pair only if both
    # endpoints have completed responses.
    between =
      if length(Enum.uniq_by(samples, & &1.target)) < 2,
        do: "N/A (one endpoint has no eligible completed responses)",
        else: difference_status(map_size(groups) > 1)

    IO.puts("  Between endpoints: #{between}")

    if map_size(groups) > 1 do
      IO.puts(
        "  Comparing exact bodies, including row order and whitespace; runs selected for closing excluded."
      )

      IO.puts(
        "  Diff baseline: endpoint #{baseline.target + 1}, run #{baseline.run}. Unchanged lines are omitted."
      )

      groups
      |> Enum.sort_by(fn {_body, matching} ->
        first = hd(matching)
        {first.run, first.target}
      end)
      |> Enum.each(fn {body, matching} ->
        locations = Enum.map_join(matching, ", ", &"endpoint #{&1.target + 1} run #{&1.run}")
        IO.puts("\n  Response shared by: #{locations}")

        if body == baseline.body do
          IO.puts("  Baseline response")
        else
          report_line_diff(baseline.body, body)
        end
      end)
    end
  end

  @max_diff_lines 50

  # Linear-time multiset diff of lines. An ordered diff (List.myers_difference)
  # is O(N * D) and effectively never finishes on large reordered responses.
  defp report_line_diff(baseline, body) do
    base = String.split(baseline, "\n")
    other = String.split(body, "\n")
    removed = lines_missing_from(base, other)
    added = lines_missing_from(other, base)

    if removed == [] and added == [] do
      IO.puts("  Same lines, different order")
    else
      print_lines("-", removed)
      print_lines("+", added)
    end
  end

  defp lines_missing_from(lines, other) do
    {missing, _remaining} =
      Enum.flat_map_reduce(lines, Enum.frequencies(other), fn line, remaining ->
        case Map.get(remaining, line, 0) do
          0 -> {[line], remaining}
          n -> {[], Map.put(remaining, line, n - 1)}
        end
      end)

    missing
  end

  defp print_lines(prefix, lines) do
    lines |> Enum.take(@max_diff_lines) |> Enum.each(&IO.puts("#{prefix} " <> inspect(&1)))

    if length(lines) > @max_diff_lines do
      IO.puts("#{prefix} ... and #{length(lines) - @max_diff_lines} more lines")
    end
  end

  defp count_rows(body) do
    body
    |> String.trim_leading("\uFEFF")
    |> QueryCSV.parse_string(skip_headers: false)
    |> case do
      [] -> raise "empty CSV response: expected a header row"
      [_header | rows] -> length(rows)
    end
  end

  defp difference_status(differed) do
    {color, label} = if differed, do: {:red, "YES"}, else: {:green, "no"}
    IO.ANSI.format([color, label, :reset]) |> IO.iodata_to_binary()
  end

  defp format(value), do: :erlang.float_to_binary(value, decimals: 3)
end

try do
  QueryBenchmark.main(System.argv())
rescue
  exception ->
    IO.puts(:stderr, "Error: #{Exception.message(exception)}")
    System.halt(1)
end
