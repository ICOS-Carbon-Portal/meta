defmodule BiblioMaterializer.LifecycleTest do
  use ExUnit.Case, async: true

  alias BiblioMaterializer.{Application, Cache, DataCiteQueue, Population, Run}

  test "single-pass mode and concurrency 16 are the defaults" do
    assert Application.options([]) == [continuous: false, concurrency: 16]
  end

  test "continuous mode and concurrency are independent flags" do
    assert Application.options(["--continuous"]) == [continuous: true, concurrency: 16]

    assert Application.options(["--continuous", "--concurrency", "7"]) ==
             [continuous: true, concurrency: 7]

    assert Application.options(["--concurrency", "3"]) ==
             [continuous: false, concurrency: 3]

    assert Population.run_options(7) == [concurrency: 7]
  end

  test "concurrency must be positive" do
    assert_raise ArgumentError, ~r/--concurrency must be a positive integer/, fn ->
      Application.options(["--concurrency", "0"])
    end
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
