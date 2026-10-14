defmodule Mix.Tasks.Bench do
  @moduledoc "See `mix bench --help`."
  use Mix.Task

  @shortdoc "Measures (and compares) data object landing page load times"

  @requirements ["app.start"]

  @impl true
  def run(argv), do: LandingBench.CLI.main(argv)
end
