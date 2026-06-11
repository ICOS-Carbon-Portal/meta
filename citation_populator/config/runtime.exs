import Config

config :logger, level: :info

config :citation_populator,
  # Virtuoso itself (the Scala service's default too) — NOT the meta-service
  # host, whose /sparql route is read-only and caps result sets.
  virtuoso_host: System.get_env("VIRTUOSO_HOST", "http://localhost:8890"),
  virtuoso_username: System.get_env("VIRTUOSO_USERNAME", "dba"),
  virtuoso_password: System.get_env("VIRTUOSO_PASSWORD", "dba"),
  derived_citations_graph:
    System.get_env("DERIVED_CITATIONS_GRAPH", "http://meta.icos-cp.eu/derived/citations/"),
  max_concurrency: String.to_integer(System.get_env("MAX_CONCURRENCY", "10"))
