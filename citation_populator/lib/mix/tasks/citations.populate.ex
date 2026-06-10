defmodule Mix.Tasks.Citations.Populate do
  @shortdoc "Populates missing citation triples in Virtuoso via raw SPARQL over HTTP"

  @moduledoc """
  #{@shortdoc}.

      mix citations.populate

  Configuration is taken from environment variables (see the project README):
  `VIRTUOSO_HOST`, `VIRTUOSO_USERNAME`, `VIRTUOSO_PASSWORD`,
  `CITATIONS_SERVICE_URL` and `DERIVED_CITATIONS_GRAPH`.
  """

  use Mix.Task

  @requirements ["app.start"]

  @impl Mix.Task
  def run(_args) do
    CitationPopulator.run()
  end
end
