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

  Entries never expire. A single population pass is a point-in-time snapshot
  of the triplestore, so within one run the reference data is treated as
  immutable — exactly the assumption the un-cached code already relied on by
  reading each object independently.
  """

  use GenServer

  @table __MODULE__

  def start_link(_opts), do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)

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
