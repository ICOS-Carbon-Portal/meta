defmodule CitationPopulator.Reader do
  @moduledoc """
  Reads the citation-relevant fields of data objects, document objects and
  collections from the triplestore with raw SPARQL — the (much smaller)
  counterpart of StaticObjectReader/CollectionReader.
  """

  alias CitationPopulator.{Cache, Rdf}
  import CitationPopulator.Util, only: [last_segment: 1]

  @empty_spec %{
    uri: nil,
    label: nil,
    data_level: nil,
    dataset_type: nil,
    project_uri: nil,
    project_label: nil,
    theme: nil,
    format: nil,
    dataset: nil
  }

  def data_object(uri) do
    core =
      Rdf.select_one("""
      SELECT * WHERE {
        OPTIONAL { <#{uri}> cpmeta:hasName ?fileName }
        OPTIONAL { <#{uri}> cpmeta:hasSizeInBytes ?size }
        OPTIONAL { <#{uri}> cpmeta:hasDoi ?doi }
        OPTIONAL { <#{uri}> cpmeta:hasKeywords ?keywords }
        OPTIONAL { <#{uri}> cpmeta:hasObjectSpec ?spec }
        OPTIONAL { <#{uri}> cpmeta:hasActualColumnNames ?actualColumns }
        OPTIONAL { <#{uri}> dcterms:title ?title }
        OPTIONAL { <#{uri}> dcterms:description ?description }
        OPTIONAL { <#{uri}> cpmeta:hasStartTime ?startTime }
        OPTIONAL { <#{uri}> cpmeta:hasEndTime ?endTime }
      } LIMIT 1
      """)

    spec_uri = Rdf.val(core, "spec")

    %{
      uri: uri,
      hash_id: last_segment(uri),
      file_name: Rdf.val(core, "fileName"),
      has_size: Rdf.val(core, "size") != nil,
      doi_raw: Rdf.val(core, "doi"),
      keywords_raw: Rdf.val(core, "keywords"),
      actual_columns_json: Rdf.val(core, "actualColumns"),
      l3: %{
        title: Rdf.val(core, "title"),
        description: Rdf.val(core, "description"),
        start: Rdf.parse_datetime(Rdf.val(core, "startTime")),
        stop: Rdf.parse_datetime(Rdf.val(core, "endTime"))
      },
      spec: if(spec_uri, do: read_spec(spec_uri), else: @empty_spec),
      acq: read_acquisition(uri),
      prod: read_production(uri),
      subm: read_submission(uri)
    }
  end

  def doc_object(uri) do
    core =
      Rdf.select_one("""
      SELECT * WHERE {
        OPTIONAL { <#{uri}> cpmeta:hasName ?fileName }
        OPTIONAL { <#{uri}> cpmeta:hasDoi ?doi }
        OPTIONAL { <#{uri}> cpmeta:hasKeywords ?keywords }
        OPTIONAL { <#{uri}> dcterms:title ?title }
      } LIMIT 1
      """)

    %{
      uri: uri,
      hash_id: last_segment(uri),
      file_name: Rdf.val(core, "fileName"),
      doi_raw: Rdf.val(core, "doi"),
      keywords_raw: Rdf.val(core, "keywords"),
      doc_title: Rdf.val(core, "title"),
      creator_uris: Rdf.values("SELECT ?c WHERE { <#{uri}> dcterms:creator ?c }", "c"),
      subm: read_submission(uri)
    }
  end

  def collection(uri) do
    core =
      Rdf.select_one("""
      SELECT * WHERE {
        OPTIONAL { <#{uri}> dcterms:title ?title }
        OPTIONAL { <#{uri}> cpmeta:hasDoi ?doi }
      } LIMIT 1
      """)

    %{uri: uri, title: Rdf.val(core, "title"), doi_raw: Rdf.val(core, "doi")}
  end

  # Object specs are shared across all objects that use them, so read each
  # one from Virtuoso only once per run.
  defp read_spec(spec_uri) do
    Cache.fetch({:spec, spec_uri}, fn -> read_spec_uncached(spec_uri) end)
  end

  defp read_spec_uncached(spec_uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        OPTIONAL { <#{spec_uri}> rdfs:label ?label }
        OPTIONAL { <#{spec_uri}> cpmeta:hasDataLevel ?dataLevel }
        OPTIONAL { <#{spec_uri}> cpmeta:hasSpecificDatasetType ?datasetType }
        OPTIONAL { <#{spec_uri}> cpmeta:hasAssociatedProject ?project .
                   OPTIONAL { ?project rdfs:label ?projectLabel } }
        OPTIONAL { <#{spec_uri}> cpmeta:hasDataTheme ?theme }
        OPTIONAL { <#{spec_uri}> cpmeta:hasFormat ?format }
        OPTIONAL { <#{spec_uri}> cpmeta:containsDataset ?dataset }
      } LIMIT 1
      """)

    %{
      uri: spec_uri,
      label: Rdf.val(row, "label"),
      data_level: Rdf.parse_int(Rdf.val(row, "dataLevel")),
      dataset_type: dataset_type(Rdf.val(row, "datasetType")),
      project_uri: Rdf.val(row, "project"),
      project_label: Rdf.val(row, "projectLabel"),
      theme: Rdf.val(row, "theme"),
      format: Rdf.val(row, "format"),
      dataset: Rdf.val(row, "dataset")
    }
  end

  defp dataset_type(uri) do
    cond do
      uri == CitationPopulator.Vocab.station_time_series_dataset() -> :station_time_series
      uri == CitationPopulator.Vocab.spatio_temporal_dataset() -> :spatio_temporal
      true -> nil
    end
  end

  defp read_acquisition(uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        <#{uri}> cpmeta:wasAcquiredBy ?acq .
        OPTIONAL { ?acq prov:startedAtTime ?start }
        OPTIONAL { ?acq prov:endedAtTime ?stop }
        OPTIONAL { ?acq prov:wasAssociatedWith ?station .
                   OPTIONAL { ?station cpmeta:hasName ?stationName } }
        OPTIONAL { ?acq cpmeta:hasSamplingHeight ?samplingHeight }
        OPTIONAL { ?acq cpmeta:wasPerformedAt ?site .
                   OPTIONAL { ?site cpmeta:hasSpatialCoverage ?siteCov .
                              OPTIONAL { ?siteCov rdfs:label ?siteLocationLabel } } }
        OPTIONAL { ?acq cpmeta:hasSamplingPoint ?sp . OPTIONAL { ?sp rdfs:label ?samplingPointLabel } }
      } LIMIT 1
      """)

    station = Rdf.val(row, "station")

    %{
      start: Rdf.parse_datetime(Rdf.val(row, "start")),
      stop: Rdf.parse_datetime(Rdf.val(row, "stop")),
      station_uri: station,
      station_name: Rdf.val(row, "stationName"),
      station_types: if(station, do: station_types(station), else: []),
      sampling_height: Rdf.parse_float(Rdf.val(row, "samplingHeight")),
      site_location_label: Rdf.val(row, "siteLocationLabel"),
      sampling_point_label: Rdf.val(row, "samplingPointLabel")
    }
  end

  # The same station backs every object acquired there; its rdf:types don't
  # change within a run.
  defp station_types(station) do
    Cache.fetch({:station_types, station}, fn ->
      Rdf.values("SELECT ?t WHERE { <#{station}> a ?t }", "t")
    end)
  end

  defp read_production(uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        <#{uri}> cpmeta:wasProducedBy ?prod .
        OPTIONAL { ?prod cpmeta:wasPerformedBy ?creator }
        OPTIONAL { ?prod cpmeta:hasEndTime ?dateTime }
      } LIMIT 1
      """)

    case Rdf.val(row, "prod") do
      nil ->
        %{exists: false, creator_uri: nil, contributor_uris: [], date_time: nil}

      prod ->
        %{
          exists: true,
          creator_uri: Rdf.val(row, "creator"),
          contributor_uris:
            Rdf.values("SELECT ?c WHERE { <#{prod}> cpmeta:wasParticipatedInBy ?c }", "c"),
          date_time: Rdf.parse_datetime(Rdf.val(row, "dateTime"))
        }
    end
  end

  defp read_submission(uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        <#{uri}> cpmeta:wasSubmittedBy ?subm .
        OPTIONAL { ?subm prov:endedAtTime ?stop }
        OPTIONAL { ?subm prov:wasAssociatedWith ?org . OPTIONAL { ?org cpmeta:hasName ?submitterName } }
      } LIMIT 1
      """)

    %{
      stop: Rdf.parse_datetime(Rdf.val(row, "stop")),
      submitter_name: Rdf.val(row, "submitterName")
    }
  end
end
