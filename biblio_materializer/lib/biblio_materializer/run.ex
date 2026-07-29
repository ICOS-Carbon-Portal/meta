defmodule BiblioMaterializer.Run do
  @moduledoc false

  use Supervisor

  alias BiblioMaterializer.Cache

  def start_link(_opts), do: Supervisor.start_link(__MODULE__, [])

  def context(supervisor) do
    cache = child_pid(supervisor, Cache)
    queue = child_pid(supervisor, BiblioMaterializer.DataCiteQueue)

    %{cache: Cache.table(cache), queue: queue}
  end

  @impl true
  def init([]) do
    children = [
      Cache,
      BiblioMaterializer.DataCiteQueue
    ]

    Supervisor.init(children, strategy: :one_for_one)
  end

  defp child_pid(supervisor, id) do
    [{^id, pid, _type, _modules}] =
      Supervisor.which_children(supervisor) |> Enum.filter(&match?({^id, _, _, _}, &1))

    pid
  end
end
