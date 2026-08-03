defmodule BiblioMaterializer.Envri do
  @moduledoc """
  ENVRI inference and per-ENVRI constants, ported from meta core's
  reference.conf (envriConfigs, handleProxies), the eu.icoscp.envri enum
  (short/long names), the handle config and CitationMaker.defaultLicence.

  A subject belongs to an ENVRI when its hostname matches one of the
  ENVRI's hosts and the URI starts with the object/collection prefix
  (`<dataItemPrefix>objects/` or `<dataItemPrefix>collections/`), mirroring
  EnvriResolver.infer + CitationProvider.inferObjectEnvri/inferCollEnvri.
  """

  @doi_proxy "https://doi.org/"
  @handle_proxy "https://hdl.handle.net/"

  @configs [
    icos: %{
      hosts: ["data.icos-cp.eu", "meta.icos-cp.eu"],
      data_item_prefix: "https://meta.icos-cp.eu/",
      short_name: "ICOS",
      long_name: "Integrated Carbon Observation System",
      utc_offset_seconds: 0,
      handle_prefix: "11676"
    },
    sites: %{
      hosts: ["data.fieldsites.se", "meta.fieldsites.se"],
      data_item_prefix: "https://meta.fieldsites.se/",
      short_name: "SITES",
      long_name: "Swedish Infrastructure for Ecosystem Science",
      utc_offset_seconds: 3600,
      handle_prefix: "11676.1"
    },
    icos_cities: %{
      hosts: ["citydata.icos-cp.eu", "citymeta.icos-cp.eu"],
      data_item_prefix: "https://citymeta.icos-cp.eu/",
      short_name: "ICOS Cities",
      long_name: "Pilot Applications in Urban Landscapes",
      # no handle prefix configured for ICOS Cities in meta either
      utc_offset_seconds: 0,
      handle_prefix: nil
    }
  ]

  def object_envri(uri), do: infer(uri, "objects/")
  def collection_envri(uri), do: infer(uri, "collections/")

  defp infer(uri, path_prefix) do
    host = URI.parse(uri).host

    Enum.find_value(@configs, fn {envri, conf} ->
      if host in conf.hosts and String.starts_with?(uri, conf.data_item_prefix <> path_prefix),
        do: envri
    end)
  end

  def short_name(envri), do: conf(envri).short_name
  def long_name(envri), do: conf(envri).long_name
  def utc_offset(envri), do: conf(envri).utc_offset_seconds
  def handle_prefix(envri), do: conf(envri).handle_prefix

  defp conf(envri), do: Keyword.fetch!(@configs, envri)

  @doc "The URL cited for the object: the DOI proxied via doi.org, else the handle PID via hdl.handle.net."
  def pid_url(doi_raw, pid) do
    cond do
      doi_raw -> @doi_proxy <> doi_raw
      pid -> @handle_proxy <> pid
      true -> nil
    end
  end

  @ccby4 "https://creativecommons.org/licenses/by/4.0"

  def default_licence(:sites) do
    %{
      "url" => "https://meta.fieldsites.se/ontologies/sites/sitesLicence",
      "name" => "SITES CCBY4 Data Licence",
      "webpage" => "https://data.fieldsites.se/licence",
      "baseLicence" => @ccby4
    }
  end

  def default_licence(_icos_or_cities) do
    %{
      "url" => "http://meta.icos-cp.eu/ontologies/cpmeta/icosLicence",
      "name" => "ICOS CCBY4 Data Licence",
      "webpage" => "https://data.icos-cp.eu/licence",
      "baseLicence" => @ccby4
    }
  end
end
