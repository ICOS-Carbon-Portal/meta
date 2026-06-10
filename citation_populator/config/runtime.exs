import Config

config :logger, level: :info

config :citation_populator,
  virtuoso_host: System.get_env("VIRTUOSO_HOST", "http://localhost:8890"),
  virtuoso_username: System.get_env("VIRTUOSO_USERNAME", "dba"),
  virtuoso_password: System.get_env("VIRTUOSO_PASSWORD", "dba"),
  citations_service_url: System.get_env("CITATIONS_SERVICE_URL", "http://127.0.0.1:9095"),
  derived_citations_graph:
    System.get_env("DERIVED_CITATIONS_GRAPH", "http://meta.icos-cp.eu/derived/citations/")
