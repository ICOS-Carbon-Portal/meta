defmodule CitationPopulator.MixProject do
  use Mix.Project

  def project do
    [
      app: :citation_populator,
      version: "0.1.0",
      elixir: "~> 1.18",
      start_permanent: Mix.env() == :prod,
      aliases: aliases(),
      deps: [
        {:req, "~> 0.5"}
      ]
    ]
  end

  defp aliases do
    [
      test: "test --no-start"
    ]
  end

  def application do
    [
      extra_applications: [:logger],
      mod: {CitationPopulator.Application, []}
    ]
  end
end
