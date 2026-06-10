defmodule CitationPopulator.Application do
  @moduledoc false

  use Application

  @impl true
  def start(_type, _args) do
    # httpc defaults to 2 keep-alive connections per host, which would
    # bottleneck concurrent subject processing.
    :httpc.set_options(max_sessions: 64)

    # Shared counter behind the every-N-lookups DataCite progress log.
    :persistent_term.put(
      {CitationPopulator.DataCite, :lookup_counter},
      :atomics.new(1, [])
    )

    children = [
      {Task.Supervisor, name: CitationPopulator.TaskSupervisor},
      CitationPopulator.DataCiteQueue
    ]

    Supervisor.start_link(children, strategy: :one_for_one, name: CitationPopulator.Supervisor)
  end
end
