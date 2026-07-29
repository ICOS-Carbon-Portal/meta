defmodule CitationPopulator.Population do
  @moduledoc false

  use GenServer
  require Logger

  @cycle_ms :timer.hours(1)

  def start_link(opts) do
    GenServer.start_link(__MODULE__, opts, name: __MODULE__)
  end

  @impl true
  def init(opts) do
    {:ok, %{single: Keyword.fetch!(opts, :single)}, {:continue, :populate}}
  end

  @impl true
  def handle_continue(:populate, state), do: populate(state)

  @impl true
  def handle_info(:populate, state), do: populate(state)

  defp populate(%{single: true} = state) do
    status = if run_once() == :ok, do: 0, else: 1
    System.stop(status)
    {:noreply, state}
  end

  defp populate(state) do
    run_once()
    Logger.info("Population pass complete; next pass in one hour")
    Process.send_after(self(), :populate, @cycle_ms)
    {:noreply, state}
  end

  defp run_once do
    try do
      CitationPopulator.run()
      :ok
    rescue
      exception ->
        Logger.error(Exception.format(:error, exception, __STACKTRACE__))
        :error
    catch
      kind, reason ->
        Logger.error(Exception.format(kind, reason, __STACKTRACE__))
        :error
    end
  end
end
