defmodule LandingBench.MixProject do
  use Mix.Project

  def project do
    [
      app: :landing_bench,
      version: "0.1.0",
      elixir: "~> 1.18",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      escript: [main_module: LandingBench.CLI]
    ]
  end

  def application do
    [extra_applications: [:logger]]
  end

  defp deps do
    [
      {:req, "~> 0.5"}
    ]
  end
end
