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

  @doc """
  Pauses slot handout for the given time, on top of any pause already in
  effect. Used when DataCite answers 429: the whole client is over the rate
  limit, so every worker should back off, not just the one that hit it.
  """
  def backoff(ms), do: GenServer.cast(__MODULE__, {:backoff, ms})

  @impl true
  def init(nil), do: {:ok, System.monotonic_time(:millisecond)}

  @impl true
  def handle_call(:permit, _from, next_at) do
    now = System.monotonic_time(:millisecond)
    slot = max(now, next_at)
    {:reply, slot - now, slot + @interval_ms}
  end

  @impl true
  def handle_cast({:backoff, ms}, next_at) do
    {:noreply, max(next_at, System.monotonic_time(:millisecond) + ms)}
  end
end
