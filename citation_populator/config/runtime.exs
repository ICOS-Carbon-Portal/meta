import Config

config :logger, level: :info

config :citation_populator,
  virtuoso_host: System.get_env("VIRTUOSO_HOST", "https://metalocal-virtuoso.icos-cp.eu"),
  virtuoso_username: System.get_env("VIRTUOSO_USERNAME", "dba"),
  virtuoso_password: System.get_env("VIRTUOSO_PASSWORD", "dba"),
  derived_citations_graph:
    System.get_env("DERIVED_CITATIONS_GRAPH", "http://meta.icos-cp.eu/derived/citations/")
