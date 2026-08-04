defmodule Keywords.CLI do
  @moduledoc """
  Standalone, one-shot tool for the two keyword passes over the Virtuoso triplestore. It
  runs the single pass its command asks for and then terminates.

  One command per pass:

    - `split [--delete-has-keywords]` runs `Keywords.Splitter.split_all/2`, a migration that
      expands the legacy comma-separated `hasKeywords` literals into singular
      `hasOwnKeyword` triples on the very same subject and graph. By default the pass is
      purely additive: the `hasKeywords` literals are left in place next to their new
      singular counterparts. Given `--delete-has-keywords` it also removes them,
      destructively but conservatively: a `hasKeywords` triple is deleted only after its
      singular counterparts have been written to the same graph and read back from the
      store, so an interrupted or partially failing run can simply be repeated. Subjects
      whose `hasKeywords` holds no parseable keyword are only warned about: re-running would
      not change them.

    - `materialize` runs `Keywords.Materializer.materialize_all/2`, which rebuilds the
      derived `hasKeyword` triples out of the `hasOwnKeyword` triples the split produced.
      Its derived graph is a cache, cleared and rebuilt on every run, so this pass is meant
      to be repeated; re-running it on a schedule (e.g. via cron) is left to the deployment.

  Either command exits non-zero if its pass failed, and the split also if any subject's
  singular triples could not be confirmed or any `hasKeywords` survived its deletion, so a
  deployment can tell a clean run from one needing a repeat.

  Note that Virtuoso's instance graphs are rebuilt from the Postgres RDF log by the meta
  application's triplestore populator, which clears and re-uploads each graph. A
  repopulation therefore undoes the split; making it permanent requires the same change in
  the RDF log. The materialized graph is not in the RDF log at all, and is simply rebuilt by
  the next `materialize`.
  """

  require Logger
  alias Keywords.{Materializer, Splitter, Virtuoso}

  @derived_graph "http://meta.icos-cp.eu/derived/keywords/"

  @usage """
  Usage: keywords <command>
    split [--delete-has-keywords]
        expand hasKeywords literals into hasOwnKeyword triples
    materialize
        rebuild the derived hasKeyword graph out of hasOwnKeyword triples
  """

  def main(args) do
    {:ok, _} = Application.ensure_all_started([:logger, :inets, :ssl])

    case parse(args) do
      {:ok, pass} ->
        System.halt(run(pass))

      :error ->
        IO.puts(:stderr, @usage)
        System.halt(2)
    end
  end

  defp run(pass) do
    host = System.get_env("VIRTUOSO_HOST", "http://localhost:8890")
    username = System.get_env("VIRTUOSO_USERNAME", "dummy")
    password = System.get_env("VIRTUOSO_PASSWORD", "dummy")
    repo = Virtuoso.new(host, username, password)

    try do
      clean =
        case pass do
          {:split, delete?} ->
            Logger.info(
              "Keyword splitting run started against #{host}, " <>
                "hasKeywords deletion #{if delete?, do: "on", else: "off"}"
            )

            summary = Splitter.split_all(repo, delete?)
            Logger.info("Keyword splitting done: #{Splitter.Summary.to_string(summary)}")
            summary.unconfirmed == [] && summary.deletion_failures == []

          :materialize ->
            Logger.info(
              "Keyword materialization run started against #{host}, graph #{@derived_graph}"
            )

            written = Materializer.materialize_all(repo, @derived_graph)
            Logger.info("Keyword materialization done, #{written} triples in derived graph")
            true
        end

      if clean, do: 0, else: 1
    catch
      kind, error ->
        Logger.error(
          "Keyword #{name(pass)} failed: #{Exception.format(kind, error, __STACKTRACE__)}"
        )

        1
    after
      Logger.flush()
    end
  end

  defp parse(["split"]), do: {:ok, {:split, false}}
  defp parse(["split", "--delete-has-keywords"]), do: {:ok, {:split, true}}
  defp parse(["materialize"]), do: {:ok, :materialize}

  defp parse([]) do
    IO.puts(:stderr, "No command given")
    :error
  end

  defp parse(other) do
    IO.puts(:stderr, "Unrecognized arguments: #{Enum.join(other, " ")}")
    :error
  end

  # The pass a command line asks for, named for the log.
  defp name({:split, _}), do: "splitting"
  defp name(:materialize), do: "materialization"
end
