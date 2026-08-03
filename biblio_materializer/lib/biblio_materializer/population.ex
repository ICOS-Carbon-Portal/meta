defmodule BiblioMaterializer.Population do
  @moduledoc false

  use GenServer
  require Logger

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

  @doc false
  def run_options(true), do: []
  def run_options(false), do: [concurrency: 1]

  defp populate(%{single: true} = state) do
    status = if run_once(run_options(true)) == :ok, do: 0, else: 1
    System.stop(status)
    {:noreply, state}
  end

  defp populate(state) do
    run_once(run_options(false))
    Logger.info("Population pass complete; starting next pass")
    send(self(), :populate)
    {:noreply, state}
  end

  defp run_once(opts) do
    try do
      BiblioMaterializer.run(opts)
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
