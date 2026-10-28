defmodule LandingBench.Stats do
  @moduledoc "Summary statistics and CSV export of landing page timings."

  import LandingBench.Output, only: [build_info_label: 1, fmt_ms: 1, match_label: 1]

  @doc """
  The result of a landing page `:object` event (see `LandingBench.Output`), as used
  by the functions of this module: the primary response, with the secondary
  response and the comparison result under `:secondary` and `:match` if there is one.
  """
  def result(%{type: :object, primary: primary, secondary: nil}), do: Map.delete(primary, :body)

  def result(%{type: :object, uri: uri, primary: primary, secondary: secondary, match: match}) do
    primary
    |> Map.delete(:body)
    |> Map.merge(%{id: object_id(uri), secondary: Map.delete(secondary, :body), match: match})
  end

  # The hash of a data object, from its URI (or a bare hash, as given with `--object`)
  defp object_id(uri), do: uri |> URI.parse() |> Map.fetch!(:path) |> Path.basename()

  @doc "Key figures of a run, for the index of stored runs."
  def overview(results) do
    timings = ok_timings(results) |> Enum.sort()

    %{
      requests: length(results),
      ok: length(timings),
      median_ms: if(timings != [], do: percentile(timings, 50)),
      mismatches:
        if(Enum.any?(results, &Map.has_key?(&1, :secondary)),
          do: Enum.count(results, &LandingBench.Diff.mismatch?(&1[:match]))
        )
    }
  end

  @doc """
  Prints the summary of `results`, with the `:build_info` events (see
  `LandingBench.Output`) of the primary and, if any, secondary meta host, in that
  order; runs stored before build info was recorded have none.
  """
  def print_summary(results, build_infos) do
    [primary_info, secondary_info] = Enum.take(build_infos ++ [nil, nil], 2)

    case Enum.filter(results, &Map.has_key?(&1, :secondary)) do
      [] ->
        print_timings("Summary", results, primary_info)

      with_secondary ->
        print_timings("Summary, primary meta host", results, primary_info)

        print_timings(
          "Summary, secondary meta host",
          Enum.map(with_secondary, & &1.secondary),
          secondary_info
        )

        print_comparison(with_secondary)
    end
  end

  defp print_comparison(results) do
    IO.puts("\n--- Comparison of responses ---")

    results
    |> Enum.frequencies_by(fn r -> match_category(r.match) end)
    |> Enum.sort()
    |> Enum.each(fn {label, n} ->
      IO.puts("#{String.pad_leading(Integer.to_string(n), 5)}  #{label}")
    end)

    case mismatching_ids(results) do
      [] ->
        :ok

      ids ->
        IO.puts("\n--- Mismatching objects ---")
        Enum.each(ids, &IO.puts/1)
    end
  end

  @doc "The hashes of the data objects whose responses mismatch."
  def mismatching_ids(results),
    do: for(r <- results, LandingBench.Diff.mismatch?(r[:match]), do: r.id)

  defp match_category({:different, _line}), do: "MISMATCH: body differs"
  defp match_category(match), do: match_label(match)

  defp print_timings(title, results, build_info) do
    timings = ok_timings(results)
    non_ok = Enum.count(results, &(&1.error == nil and &1.status != 200))
    errors = Enum.count(results, &(&1.error != nil))

    IO.puts("\n--- #{title} ---")
    if build_info, do: IO.puts(build_info_label(build_info))

    IO.puts(
      "Requests: #{length(results)}  OK: #{length(timings)}  non-200: #{non_ok}  errors: #{errors}"
    )

    case Enum.sort(timings) do
      [] ->
        IO.puts("No successful landing page fetches.")

      sorted ->
        n = length(sorted)
        mean = Enum.sum(sorted) / n

        for {label, value} <- [
              {"min", hd(sorted)},
              {"mean", mean},
              {"median", percentile(sorted, 50)},
              {"p90", percentile(sorted, 90)},
              {"p95", percentile(sorted, 95)},
              {"max", List.last(sorted)}
            ] do
          IO.puts("#{String.pad_trailing(label, 7)} #{fmt_ms(value)}")
        end
    end
  end

  defp ok_timings(results), do: for(%{error: nil, status: 200, ms: ms} <- results, do: ms)

  # Nearest-rank percentile of an already sorted list
  defp percentile(sorted, p) do
    rank = max(ceil(p / 100 * length(sorted)), 1)
    Enum.at(sorted, rank - 1)
  end

  @csv_header "url,status,ms,bytes,error"
  @csv_secondary_header "secondary_url,secondary_status,secondary_ms,secondary_bytes,secondary_error,match"

  def write_csv(results, path) do
    with_secondary = Enum.any?(results, &Map.has_key?(&1, :secondary))

    lines =
      for r <- results do
        if with_secondary do
          Enum.join(
            csv_fields(r) ++
              csv_fields(r.secondary) ++ [csv_escape(match_label(r.match))],
            ","
          )
        else
          Enum.join(csv_fields(r), ",")
        end
      end

    header = if with_secondary, do: @csv_header <> "," <> @csv_secondary_header, else: @csv_header
    File.write!(path, Enum.join([header | lines], "\n") <> "\n")
    IO.puts("Wrote #{length(results)} rows to #{path}")
  end

  defp csv_fields(r), do: [r.url, r.status || "", r.ms || "", r.bytes, csv_escape(r.error || "")]

  defp csv_escape(s), do: ~s("#{String.replace(s, "\"", "\"\"")}")
end
