defmodule CitationPopulator.Application do
  @moduledoc false

  use Application
  require Logger

  @impl true
  def start(_type, _args) do
    # Sparql.Auth outlives a single run (it holds the update endpoint's auth
    # challenge, which every writer shares) so it lives here rather than in
    # the per-run supervisor, and it must be up before the first update.
    children = [CitationPopulator.Sparql.Auth | run_children()]

    Supervisor.start_link(children, strategy: :one_for_one, name: CitationPopulator.Supervisor)
  end

  # When started for real (not under `mix test`), kick off a single population
  # pass as soon as the supervision tree is up, then stop the VM. The Task is
  # the last child so the queue is already running; its child spec defaults to
  # restart: :temporary, so it is not restarted after the (one) run completes.
  defp run_children do
    if Application.get_env(:citation_populator, :run_on_start, false) do
      [{Task, &run_and_stop/0}]
    else
      []
    end
  end

  defp run_and_stop do
    status =
      try do
        CitationPopulator.run()
        0
      rescue
        exception ->
          Logger.error(Exception.format(:error, exception, __STACKTRACE__))
          1
      catch
        kind, reason ->
          Logger.error(Exception.format(kind, reason, __STACKTRACE__))
          1
      end

    System.stop(status)
  end
end
