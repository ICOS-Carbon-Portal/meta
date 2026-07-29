defmodule CitationPopulator.Cache do
  @moduledoc """
  Process-wide memoization of slowly-changing reference data — object specs,
  licences, station memberships (the authors), dataset column definitions,
  station fundings and agents — shared across all worker processes through a
  single public ETS table.

  The citable-object count dwarfs the number of distinct specs, licences,
  stations and people those objects point at, and every object from the same
  station (or of the same spec) re-runs the identical reference lookups. A
  URI-keyed cache collapses that: each distinct reference entity is read from
  Virtuoso once per run instead of once per object.

  Reference entries never expire. A single population pass is a point-in-time
  snapshot of the triplestore, so within one run the reference data is treated
  as immutable — exactly the assumption the un-cached code already relied on
  by reading each object independently.

  The table also carries the per-subject fields
  [`Subject`](`CitationPopulator.Subject`) prefetches a batch at a time. Those
  serve one subject each rather than being shared, so they are put and deleted
  explicitly (`put/3`, `get/2`, `delete/2`) with the batch's lifetime rather
  than memoized for the run.
  """

  use GenServer

  @table __MODULE__

  def start_link(opts) do
    GenServer.start_link(__MODULE__, nil, name: Keyword.get(opts, :name, __MODULE__))
  end

  @doc """
  Returns the cached value for `key`, computing it with `fun` (and storing
  the result) on a miss. `key` is namespaced by the caller (e.g. `{:spec,
  uri}`) so different lookups over the same URI do not collide.

  Concurrent misses on the same key may run `fun` more than once; that is
  harmless — the cached reads are pure and idempotent, so every computation
  yields the same value and the last write simply wins.
  """
  def table(cache), do: GenServer.call(cache, :table)

  def fetch(table, key, fun) do
    case :ets.lookup(table, key) do
      [{^key, value}] ->
        value

      [] ->
        value = fun.()
        :ets.insert(table, {key, value})
        value
    end
  end

  @doc "The stored value for `key` as `{:ok, value}`, or `:miss`."
  def get(table, key) do
    case :ets.lookup(table, key) do
      [{^key, value}] -> {:ok, value}
      [] -> :miss
    end
  end

  @doc "Stores `value` under `key`, replacing any previous one."
  def put(table, key, value), do: :ets.insert(table, {key, value})

  @doc "Drops `key`, whether or not it was stored."
  def delete(table, key), do: :ets.delete(table, key)

  @impl true
  def init(nil) do
    table =
      :ets.new(@table, [
        :set,
        :public,
        read_concurrency: true,
        write_concurrency: true
      ])

    {:ok, table}
  end

  @impl true
  def handle_call(:table, _from, table), do: {:reply, table, table}
end
