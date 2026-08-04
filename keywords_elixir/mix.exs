defmodule Keywords.MixProject do
  use Mix.Project

  def project do
    [
      app: :keywords,
      version: "0.1.0",
      elixir: "~> 1.17",
      escript: [main_module: Keywords.CLI],
      deps: []
    ]
  end

  def application do
    [extra_applications: [:logger, :inets, :ssl]]
  end
end
