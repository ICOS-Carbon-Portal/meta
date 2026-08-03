defmodule BiblioMaterializer.Application do
  @moduledoc false

  use Application

  @impl true
  def start(_type, _args) do
    opts = options(System.argv())

    # Sparql.Auth outlives a single run (it holds the update endpoint's auth
    # challenge, which every writer shares) so it lives here rather than in
    # the per-run supervisor, and it must be up before the first update.
    children = [
      BiblioMaterializer.Sparql.Auth,
      {BiblioMaterializer.Population, opts}
    ]

    Supervisor.start_link(children, strategy: :one_for_one, name: BiblioMaterializer.Supervisor)
  end

  @doc false
  def options(args) do
    {opts, []} =
      OptionParser.parse!(args,
        strict: [continuous: :boolean, concurrency: :integer]
      )

    concurrency = Keyword.get(opts, :concurrency, BiblioMaterializer.default_concurrency())

    if concurrency <= 0 do
      raise ArgumentError, "--concurrency must be a positive integer"
    end

    [continuous: Keyword.get(opts, :continuous, false), concurrency: concurrency]
  end
end
