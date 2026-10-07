defmodule LandingBench.CLI do
  @moduledoc false

  @usage """
  Usage: landing_bench HOST [options]

  HOST is the data host where the portal runs, e.g. data.icos-cp.eu or
  https://datalocal.icos-cp.eu (https:// is assumed if no scheme is given).

  Options:
    -s, --secondary HOST
                        also fetch each landing page from this meta host, e.g.
                        meta.icos-cp.eu or http://localhost:9094, timing it and
                        comparing the response with the primary meta host's
    -m, --max N         maximum number of landing pages to fetch (default 20)
    -d, --delay MS      pause between landing page fetches in ms (default 1000)
    -j, --jitter MS     add a random 0..MS ms to each pause (default 0)
    -t, --timeout MS    per-request receive timeout in ms (default 60000)
    -k, --insecure      skip TLS certificate verification (local dev hosts)
        --csv PATH      also write per-request results to a CSV file
    -h, --help          show this help
  """

  def main(argv) do
    {opts, args, invalid} =
      OptionParser.parse(argv,
        strict: [
          secondary: :string,
          max: :integer,
          delay: :integer,
          jitter: :integer,
          timeout: :integer,
          insecure: :boolean,
          csv: :string,
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
        IO.puts(:stderr, @usage)
        System.halt(1)

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
          csv: opts[:csv]
        ]

        case LandingBench.run(run_opts) do
          :ok ->
            :ok

          {:error, err} ->
            IO.puts(:stderr, "Error: #{format_error(err)}")
            System.halt(1)
        end
    end
  end

  defp format_error(err) when is_binary(err), do: err
  defp format_error(err) when is_exception(err), do: Exception.message(err)
  defp format_error(err), do: inspect(err)
end
