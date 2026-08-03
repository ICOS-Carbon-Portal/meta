defmodule BiblioMaterializer.Run do
  @moduledoc false

  use Supervisor

  alias BiblioMaterializer.Cache

  def start_link(opts), do: Supervisor.start_link(__MODULE__, opts)

  def context(supervisor) do
    cache = child_pid(supervisor, Cache)
    queue = child_pid(supervisor, BiblioMaterializer.DataCiteQueue)

    %{cache: Cache.table(cache), queue: queue}
  end

  @impl true
  def init(opts) do
    concurrency =
      Keyword.get_lazy(opts, :concurrency, fn ->
        Application.fetch_env!(:biblio_materializer, :max_concurrency)
      end)

    children = [
      Cache,
      {BiblioMaterializer.DataCiteQueue, concurrency: concurrency}
    ]

    Supervisor.init(children, strategy: :one_for_one)
  end

  defp child_pid(supervisor, id) do
    [{^id, pid, _type, _modules}] =
      Supervisor.which_children(supervisor) |> Enum.filter(&match?({^id, _, _, _}, &1))

    pid
  end
end
