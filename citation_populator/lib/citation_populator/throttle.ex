defmodule CitationPopulator.Throttle do
  @moduledoc """
  Global rate limiter for outgoing DataCite requests, like the Scala
  service's 10-requests-per-second throttle. Callers ask for a slot and
  sleep until it comes up; slots are handed out 100 ms apart.
  """

  use GenServer

  @interval_ms 150

  def start_link(_opts), do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)

  @doc "Blocks the caller until its rate-limit slot is due."
  def await do
    delay = GenServer.call(__MODULE__, :permit, :infinity)
    if delay > 0, do: Process.sleep(delay)
    :ok
  end

  @impl true
  def init(nil), do: {:ok, System.monotonic_time(:millisecond)}

  @impl true
  def handle_call(:permit, _from, next_at) do
    now = System.monotonic_time(:millisecond)
    slot = max(now, next_at)
    {:reply, slot - now, slot + @interval_ms}
  end
end
