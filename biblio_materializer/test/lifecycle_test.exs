defmodule BiblioMaterializer.LifecycleTest do
  use ExUnit.Case, async: true

  alias BiblioMaterializer.{Application, Cache, DataCiteQueue, Run}

  test "--single selects one-shot mode" do
    assert Application.single?(["--single"])
    assert Application.single?(["other", "--single"])
    refute Application.single?([])
    refute Application.single?(["single"])
  end

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

  test "an empty run queue drains cleanly" do
    {:ok, run} = Run.start_link([])
    %{queue: queue} = Run.context(run)

    assert DataCiteQueue.pending(queue) == 0
    assert DataCiteQueue.drain(queue) == {0, 0}

    Supervisor.stop(run)
  end

  test "drain waits for every queued job, including failed jobs" do
    {:ok, run} = Run.start_link([])
    %{queue: queue} = Run.context(run)

    assert :ok =
             DataCiteQueue.push(queue, %{
               mode: :unsupported,
               doi: {"10.1234", "TEST"},
               refs_base: %{},
               uri: "https://example.test/object",
               tag: "1/1",
               graph: "https://example.test/graph"
             })

    assert DataCiteQueue.drain(queue) == {0, 1}
    Supervisor.stop(run)
  end

  test "run children are cleaned up on an abnormal supervisor stop" do
    {:ok, run} = Run.start_link([])
    %{queue: queue} = Run.context(run)
    Process.unlink(run)

    assert :ok = Supervisor.stop(run, :abnormal)

    refute Process.alive?(queue)
  end
end
