defmodule CitationPopulator.DataCiteQueue do
  @moduledoc """
  Owns all DataCite-dependent materialization, like the Scala DataCiteQueue:
  the main pass writes a DOI subject's DataCite-independent triples, pushes a
  job here and moves on. This GenServer runs the DataCite lookups (through
  the global rate-limit throttle) on a small task pool and writes the
  remaining triples (`hasBiblioInfo`, `hasCitationString`) as they complete.

  A job is a map: %{uri, tag, graph, mode, doi, refs_base} where mode is
  :full (complete References to merge with the DataCite bundle) or
  :citation_only (only the HTML citation string is materialized). On a
  DataCite failure the subject's remaining triples are skipped for this run.
  """

  use GenServer
  require Logger

  alias CitationPopulator.{References, Writer}

  @fetch_concurrency 8

  def start_link(_opts), do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)

  def push(job), do: GenServer.cast(__MODULE__, {:push, job})

  @doc "Subjects currently queued or being fetched."
  def pending, do: GenServer.call(__MODULE__, :pending)

  @doc """
  Blocks until the queue is empty and all fetches are done; returns
  {triples_written, subjects_failed} and resets the counters.
  """
  def drain, do: GenServer.call(__MODULE__, :drain, :infinity)

  @log_every 500

  @impl true
  def init(nil) do
    {:ok, %{pending: :queue.new(), running: 0, done: 0, written: 0, failed: 0, drainers: []}}
  end

  @impl true
  def handle_cast({:push, job}, state) do
    {:noreply, start_jobs(%{state | pending: :queue.in(job, state.pending)})}
  end

  @impl true
  def handle_call(:pending, _from, state) do
    {:reply, state.running + :queue.len(state.pending), state}
  end

  def handle_call(:drain, from, state) do
    if idle?(state) do
      {stats, state} = take_stats(state)
      {:reply, stats, state}
    else
      {:noreply, %{state | drainers: [from | state.drainers]}}
    end
  end

  @impl true
  def handle_info({ref, result}, state) when is_reference(ref) do
    Process.demonitor(ref, [:flush])

    state =
      case result do
        {:ok, count} -> %{state | written: state.written + count}
        :failed -> %{state | failed: state.failed + 1}
      end

    {:noreply, task_finished(state)}
  end

  def handle_info({:DOWN, _ref, :process, _pid, reason}, state) do
    Logger.warning("DataCite queue task crashed: #{inspect(reason)}")
    {:noreply, task_finished(%{state | failed: state.failed + 1})}
  end

  defp task_finished(state) do
    state = start_jobs(%{state | running: state.running - 1, done: state.done + 1})

    if rem(state.done, @log_every) == 0 do
      Logger.info(
        "DataCite queue progress: #{state.done} subjects done " <>
          "(#{state.written} triples written, #{state.failed} failed), " <>
          "#{state.running + :queue.len(state.pending)} pending"
      )
    end

    if idle?(state) and state.drainers != [] do
      {stats, state} = take_stats(state)
      Enum.each(state.drainers, &GenServer.reply(&1, stats))
      %{state | drainers: []}
    else
      state
    end
  end

  defp start_jobs(%{running: running} = state) when running >= @fetch_concurrency, do: state

  defp start_jobs(state) do
    case :queue.out(state.pending) do
      {:empty, _queue} ->
        state

      {{:value, job}, rest} ->
        Task.Supervisor.async_nolink(CitationPopulator.TaskSupervisor, fn -> process(job) end)
        start_jobs(%{state | pending: rest, running: state.running + 1})
    end
  end

  defp idle?(state), do: state.running == 0 and :queue.is_empty(state.pending)

  defp take_stats(state),
    do: {{state.written, state.failed}, %{state | done: 0, written: 0, failed: 0}}

  defp process(job) do
    case References.complete_deferred(job.mode, job.doi, job.refs_base) do
      {:ok, refs} ->
        triples =
          case job.mode do
            :full -> Writer.biblio_triple(job.uri, refs) ++ Writer.citation_triple(job.uri, refs)
            :citation_only -> Writer.citation_triple(job.uri, refs)
          end

        count = Writer.write(job.graph, triples)
        Logger.debug("[#{job.tag}] DataCite queue: wrote #{count} triples for #{job.uri}")
        {:ok, count}

      {:error, reason} ->
        Logger.warning("[#{job.tag}] DataCite queue: skipping #{job.uri}: #{reason}")
        :failed
    end
  rescue
    e ->
      Logger.warning(
        "[#{job.tag}] DataCite queue: skipping #{job.uri}: " <>
          String.slice(Exception.format(:error, e, __STACKTRACE__), 0, 500)
      )

      :failed
  end
end
