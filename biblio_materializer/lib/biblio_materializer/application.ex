defmodule BiblioMaterializer.Application do
  @moduledoc false

  use Application

  @impl true
  def start(_type, _args) do
    # Sparql.Auth outlives a single run (it holds the update endpoint's auth
    # challenge, which every writer shares) so it lives here rather than in
    # the per-run supervisor, and it must be up before the first update.
    children = [
      BiblioMaterializer.Sparql.Auth,
      {BiblioMaterializer.Population, single: single?(System.argv())}
    ]

    Supervisor.start_link(children, strategy: :one_for_one, name: BiblioMaterializer.Supervisor)
  end

  @doc false
  def single?(args), do: "--single" in args
end
