defmodule CitationPopulator.Vocab do
  @moduledoc "URIs from the cpmeta ontology and CpVocab constants used by the citation logic."

  @cpmeta "http://meta.icos-cp.eu/ontologies/cpmeta/"

  def cpmeta, do: @cpmeta

  def data_object_class, do: @cpmeta <> "DataObject"
  def doc_object_class, do: @cpmeta <> "DocumentObject"
  def collection_class, do: @cpmeta <> "Collection"
  def has_citation_string, do: @cpmeta <> "hasCitationString"
  def has_biblio_info, do: @cpmeta <> "hasBiblioInfo"
  def dcterms_license, do: "http://purl.org/dc/terms/license"

  def icos_project, do: "http://meta.icos-cp.eu/resources/projects/icos"
  def misc_project, do: "http://meta.icos-cp.eu/resources/projects/misc"
  def atmo_theme, do: "http://meta.icos-cp.eu/resources/themes/atmosphere"
  def atm_ghg_spec, do: "http://meta.icos-cp.eu/resources/cpmeta/atmGhgProduct"
  def wdcgg_format, do: @cpmeta <> "asciiWdcggTimeSer"

  def station_time_series_dataset, do: @cpmeta <> "stationTimeSeriesDataset"
  def spatio_temporal_dataset, do: @cpmeta <> "spatioTemporalDataset"

  def rdf_seq, do: "http://www.w3.org/1999/02/22-rdf-syntax-ns#Seq"
  def rdf_member_prefix, do: "http://www.w3.org/1999/02/22-rdf-syntax-ns#_"

  @icos_like_station_types [@cpmeta <> "AS", @cpmeta <> "ES", @cpmeta <> "OS"]
  @sites_station_type "https://meta.fieldsites.se/ontologies/sites/Station"

  @doc """
  Whether the station counts as an ICOS station (IcosStationSpecifics in Scala).
  Mirrors getStationSpecifics' check order: a sites:Station is never ICOS-like,
  otherwise any of the cpmeta:AS/ES/OS types qualifies.
  """
  def icos_like_station?(types) do
    @sites_station_type not in types and Enum.any?(types, &(&1 in @icos_like_station_types))
  end
end
