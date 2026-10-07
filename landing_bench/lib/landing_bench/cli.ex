defmodule LandingBench.CLI do
  @moduledoc false

  # Where runs are stored with a bare `--store`, relative to the current directory
  @default_store "landing_bench_runs"

  @usage """
  Usage: landing_bench HOST [options]
         landing_bench view [DIR] [RUN] [view options]

  HOST is the data host where the portal runs, e.g. data.icos-cp.eu or
  https://datalocal.icos-cp.eu (https:// is assumed if no scheme is given).

  Options:
    -s, --secondary HOST
                        also fetch each landing page from this meta host, e.g.
                        meta.icos-cp.eu or http://localhost:9094, timing it and
                        comparing the response with the primary meta host's
        --diff          with --secondary, print a diff of the response bodies
                        of each mismatching landing page
    -m, --max N         maximum number of landing pages to fetch (default 20)
    -d, --delay MS      pause between landing page fetches in ms (default 1000)
    -j, --jitter MS     add a random 0..MS ms to each pause (default 0)
    -t, --timeout MS    per-request receive timeout in ms (default 60000)
    -k, --insecure      skip TLS certificate verification (local dev hosts)
        --csv PATH      also write per-request results to a CSV file
        --store [DIR]   store the run in DIR (created if needed), to be viewed
                        again later with `landing_bench view DIR`; without DIR,
                        in ./#{@default_store}. Each run gets its own
                        subdirectory, named after its start time (UTC)
    -h, --help          show this help

  `landing_bench view DIR` lists the runs stored in DIR. With RUN (a run ID
  from that list, or `latest`), the run is printed the way it was during the
  live run, followed by its summary. Without DIR, ./#{@default_store} is used.

  View options:
        --diff          print a diff of the response bodies of each
                        mismatching landing page
        --recompare     compare the stored response bodies again, with the
                        current list of known differences
        --csv PATH      also write per-request results to a CSV file
  """

  def main(["view" | argv]) do
    {opts, args, invalid} =
      OptionParser.parse(argv,
        strict: [diff: :boolean, recompare: :boolean, csv: :string, help: :boolean],
        aliases: [h: :help]
      )

    cond do
      opts[:help] ->
        IO.puts(@usage)

      invalid != [] ->
        usage_error()

      true ->
        case args do
          [] ->
            LandingBench.Viewer.list(@default_store)

          [dir, run] ->
            LandingBench.Viewer.show(dir, run, opts)

          # A single argument is a directory if there is one by that name, otherwise a run
          [arg] ->
            if File.dir?(arg),
              do: LandingBench.Viewer.list(arg),
              else: LandingBench.Viewer.show(@default_store, arg, opts)

          _ ->
            usage_error()
        end
        |> exit_on_error()
    end
  end

  def main(argv) do
    {opts, args, invalid} =
      OptionParser.parse(default_store(argv),
        strict: [
          secondary: :string,
          max: :integer,
          delay: :integer,
          jitter: :integer,
          timeout: :integer,
          insecure: :boolean,
          csv: :string,
          diff: :boolean,
          store: :string,
          help: :boolean
        ],
        aliases: [
          s: :secondary,
          m: :max,
          d: :delay,
          j: :jitter,
          t: :timeout,
          k: :insecure,
          h: :help
        ]
      )

    cond do
      opts[:help] ->
        IO.puts(@usage)

      invalid != [] or length(args) != 1 ->
        usage_error()

      true ->
        [host] = args

        run_opts = [
          host: host,
          secondary: opts[:secondary],
          max: Keyword.get(opts, :max, 20),
          delay_ms: Keyword.get(opts, :delay, 1000),
          jitter_ms: Keyword.get(opts, :jitter, 0),
          timeout_ms: Keyword.get(opts, :timeout, 60_000),
          insecure: Keyword.get(opts, :insecure, false),
          csv: opts[:csv],
          diff: Keyword.get(opts, :diff, false),
          store: opts[:store]
        ]

        run_opts |> LandingBench.run() |> exit_on_error()
    end
  end

  # OptionParser has no options with optional values, so a `--store` without
  # DIR (last, or followed by another option) is given the default directory.
  defp default_store(["--store"]), do: ["--store=" <> @default_store]

  defp default_store(["--store", "-" <> _ = next | rest]),
    do: ["--store=" <> @default_store | default_store([next | rest])]

  defp default_store([arg | rest]), do: [arg | default_store(rest)]
  defp default_store([]), do: []

  defp usage_error do
    IO.puts(:stderr, @usage)
    System.halt(1)
  end

  defp exit_on_error(:ok), do: :ok

  defp exit_on_error({:error, err}) do
    IO.puts(:stderr, "Error: #{LandingBench.Output.format_error(err)}")
    System.halt(1)
  end
end
