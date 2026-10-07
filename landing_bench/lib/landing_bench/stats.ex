defmodule LandingBench.Stats do
  @moduledoc "Summary statistics and CSV export of landing page timings."

  import LandingBench, only: [fmt_ms: 1]

  def print_summary(results) do
    timings = for %{error: nil, status: 200, ms: ms} <- results, do: ms
    non_ok = Enum.count(results, &(&1.error == nil and &1.status != 200))
    errors = Enum.count(results, &(&1.error != nil))

    IO.puts("\n--- Summary ---")

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

  # Nearest-rank percentile of an already sorted list
  defp percentile(sorted, p) do
    rank = max(ceil(p / 100 * length(sorted)), 1)
    Enum.at(sorted, rank - 1)
  end

  def write_csv(results, path) do
    lines =
      for r <- results do
        Enum.join([r.url, r.status || "", r.ms || "", r.bytes, csv_escape(r.error || "")], ",")
      end

    File.write!(path, Enum.join(["url,status,ms,bytes,error" | lines], "\n") <> "\n")
    IO.puts("Wrote #{length(results)} rows to #{path}")
  end

  defp csv_escape(s), do: ~s("#{String.replace(s, "\"", "\"\"")}")
end
