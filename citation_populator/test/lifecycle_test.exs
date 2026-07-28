defmodule CitationPopulator.LifecycleTest do
  use ExUnit.Case, async: true

  alias CitationPopulator.{Cache, Run}

  test "a newly started cache does not reuse a previous cache's values" do
    {:ok, first} = Cache.start_link([])
    first_table = Cache.table(first)
    assert Cache.fetch(first_table, :value, fn -> :first end) == :first
    GenServer.stop(first)

    {:ok, second} = Cache.start_link([])
    second_table = Cache.table(second)
    assert Cache.fetch(second_table, :value, fn -> :second end) == :second
    GenServer.stop(second)
  end

  test "a run owns and cleans up its cache and queue" do
    {:ok, run} = Run.start_link([])
    %{cache: cache, queue: queue} = Run.context(run)

    assert is_reference(cache)
    assert Process.alive?(queue)

    Supervisor.stop(run)

    refute Process.alive?(queue)
  end
end
