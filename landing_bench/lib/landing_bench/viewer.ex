defmodule LandingBench.Viewer do
  @moduledoc """
  Lists and shows runs stored with `--store` (see `LandingBench.Store`). A shown
  run is printed the way it was during the live run, followed by its summary.
  """

  alias LandingBench.{Diff, Output, Stats, Store}

  @doc "Prints a table of the runs stored in `dir`."
  def list(dir) do
    with {:ok, runs} <- Store.list(dir) do
      if runs == [] do
        IO.puts("No stored runs in #{dir}")
      else
        header = ["ID", "Status", "Host", "Secondary", "Requests", "OK", "Median", "Mismatches"]
        print_table([header | Enum.map(runs, &list_row/1)])
      end

      :ok
    end
  end

  defp list_row(run) do
    o = run["overview"] || %{}

    [
      run["id"],
      status_label(run["status"]),
      run["host"],
      run["secondary"] || "",
      to_string(o["requests"]),
      to_string(o["ok"]),
      if(o["median_ms"], do: Output.fmt_ms(o["median_ms"]), else: ""),
      to_string(o["mismatches"])
    ]
  end

  defp print_table(rows) do
    widths =
      rows
      |> Enum.zip_with(& &1)
      |> Enum.map(fn column -> column |> Enum.map(&String.length/1) |> Enum.max() end)

    for row <- rows do
      row
      |> Enum.zip(widths)
      |> Enum.map_join("  ", fn {cell, w} -> String.pad_trailing(cell, w) end)
      |> String.trim_trailing()
      |> IO.puts()
    end
  end

  @doc """
  Prints stored run `id` of `dir` (or the most recent one, if `id` is `"latest"`).

  Options:

    * `:diff` - print a diff of the response bodies of mismatching landing pages
    * `:recompare` - compare the stored response bodies again, with the current
      list of known differences, instead of showing the stored comparison results
    * `:csv` - also write the results to this CSV file
  """
  def show(dir, id, opts) do
    opts = Keyword.merge([diff: false, recompare: false], opts)

    with {:ok, meta, run_dir, events} <- Store.load(dir, id) do
      print_header(meta, opts)

      results =
        Enum.flat_map(events, fn event ->
          event = prepare(event, run_dir, opts)
          Output.print(event, opts[:diff])
          if event.type == :object, do: [Stats.result(event)], else: []
        end)

      Stats.print_summary(results)
      if path = opts[:csv], do: Stats.write_csv(results, path)
      print_footer(meta)
      :ok
    end
  end

  defp print_header(meta, opts) do
    o = meta["options"]

    IO.puts(
      "Run #{meta["id"]} (#{status_label(meta["status"])}), started #{meta["started_at"]}" <>
        if(meta["finished_at"], do: ", finished #{meta["finished_at"]}", else: "")
    )

    IO.puts(
      "Host #{meta["host"]}" <>
        if(meta["secondary"], do: ", secondary #{meta["secondary"]}", else: "") <>
        ", max #{o["max"]}, delay #{o["delay_ms"]} ms, jitter #{o["jitter_ms"]} ms, " <>
        "timeout #{o["timeout_ms"]} ms" <> if(o["insecure"], do: ", insecure", else: "")
    )

    if opts[:recompare],
      do: IO.puts("Responses recompared with the current list of known differences")

    IO.puts("")
  end

  defp print_footer(%{"status" => "failed"} = meta), do: IO.puts("\nRun failed: #{meta["error"]}")

  defp print_footer(%{"status" => "running"}),
    do: IO.puts("\nRun did not finish (interrupted, or still running)")

  defp print_footer(_meta), do: :ok

  defp status_label("running"), do: "unfinished"
  defp status_label(status), do: status

  # Loads the response bodies of a landing page event if they are needed, and
  # recompares the responses if asked to.
  defp prepare(%{type: :object, secondary: secondary} = e, run_dir, opts) when secondary != nil do
    e =
      if opts[:recompare] or (opts[:diff] and Diff.mismatch?(e.match)),
        do: %{
          e
          | primary: load_body(e.primary, run_dir),
            secondary: load_body(secondary, run_dir)
        },
        else: e

    if opts[:recompare], do: recompare(e), else: e
  end

  defp prepare(event, _run_dir, _opts), do: event

  defp load_body(result, run_dir),
    do: Map.put(result, :body, Store.read_body(run_dir, result.body_sha256))

  # Keeps the stored comparison result if a body is missing, which would
  # otherwise look like a difference.
  defp recompare(%{primary: p, secondary: s} = e) do
    if missing_body?(p) or missing_body?(s),
      do: e,
      else: %{e | match: Diff.compare(p, s)}
  end

  defp missing_body?(result), do: result.error == nil and result.body == nil
end
