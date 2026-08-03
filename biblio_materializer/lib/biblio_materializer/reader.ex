defmodule BiblioMaterializer.Reader do
  @moduledoc """
  Shapes the citation-relevant fields of data objects, document objects and
  collections — the (much smaller) counterpart of
  StaticObjectReader/CollectionReader.

  The subject's own fields come from [`Subject`](`BiblioMaterializer.Subject`),
  which reads them a batch of subjects at a time; the reference data they
  point at (specs, stations) is read here and memoized for the run.
  """

  alias BiblioMaterializer.{Cache, Rdf, Subject}
  import BiblioMaterializer.Util, only: [last_segment: 1]

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

  def data_object(uri, cache) do
    core = Subject.fetch(cache, :data_core, uri)
    spec_uri = Rdf.val(core, "spec")

    %{
      uri: uri,
      hash_id: last_segment(uri),
      file_name: Rdf.val(core, "fileName"),
      has_size: Rdf.val(core, "size") != nil,
      doi_raw: Rdf.val(core, "doi"),
      own_licence_uri: Rdf.val(core, "ownLicence"),
      keywords_raw: Rdf.val(core, "keywords"),
      actual_columns_json: Rdf.val(core, "actualColumns"),
      l3: %{
        title: Rdf.val(core, "title"),
        description: Rdf.val(core, "description"),
        start: Rdf.parse_datetime(Rdf.val(core, "startTime")),
        stop: Rdf.parse_datetime(Rdf.val(core, "endTime"))
      },
      spec: if(spec_uri, do: read_spec(cache, spec_uri), else: @empty_spec),
      acq: read_acquisition(cache, uri),
      prod: read_production(cache, uri),
      subm: read_submission(cache, uri)
    }
  end

  def doc_object(uri, cache) do
    core = Subject.fetch(cache, :doc_core, uri)

    %{
      uri: uri,
      hash_id: last_segment(uri),
      file_name: Rdf.val(core, "fileName"),
      doi_raw: Rdf.val(core, "doi"),
      own_licence_uri: Rdf.val(core, "ownLicence"),
      keywords_raw: Rdf.val(core, "keywords"),
      doc_title: Rdf.val(core, "title"),
      creator_uris: Subject.fetch(cache, :creators, uri),
      subm: read_submission(cache, uri)
    }
  end

  def collection(uri, cache) do
    core = Subject.fetch(cache, :coll_core, uri)

    %{
      uri: uri,
      title: Rdf.val(core, "title"),
      doi_raw: Rdf.val(core, "doi"),
      own_licence_uri: Rdf.val(core, "ownLicence")
    }
  end

  # Object specs are shared across all objects that use them, so read each
  # one from Virtuoso only once per run.
  defp read_spec(cache, spec_uri) do
    Cache.fetch(cache, {:spec, spec_uri}, fn -> read_spec_uncached(spec_uri) end)
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
      uri == BiblioMaterializer.Vocab.station_time_series_dataset() -> :station_time_series
      uri == BiblioMaterializer.Vocab.spatio_temporal_dataset() -> :spatio_temporal
      true -> nil
    end
  end

  defp read_acquisition(cache, uri) do
    row = Subject.fetch(cache, :acq, uri)
    station = Rdf.val(row, "station")

    %{
      start: Rdf.parse_datetime(Rdf.val(row, "start")),
      stop: Rdf.parse_datetime(Rdf.val(row, "stop")),
      station_uri: station,
      station_name: Rdf.val(row, "stationName"),
      station_types: if(station, do: station_types(cache, station), else: []),
      sampling_height: Rdf.parse_float(Rdf.val(row, "samplingHeight")),
      site_location_label: Rdf.val(row, "siteLocationLabel"),
      sampling_point_label: Rdf.val(row, "samplingPointLabel")
    }
  end

  # The same station backs every object acquired there; its rdf:types don't
  # change within a run.
  defp station_types(cache, station) do
    Cache.fetch(cache, {:station_types, station}, fn ->
      Rdf.values("SELECT ?t WHERE { <#{station}> a ?t }", "t")
    end)
  end

  defp read_production(cache, uri) do
    row = Subject.fetch(cache, :prod, uri)

    case Rdf.val(row, "prod") do
      nil ->
        %{exists: false, creator_uri: nil, contributor_uris: [], date_time: nil}

      _prod ->
        %{
          exists: true,
          creator_uri: Rdf.val(row, "creator"),
          contributor_uris: Subject.fetch(cache, :contributors, uri),
          date_time: Rdf.parse_datetime(Rdf.val(row, "dateTime"))
        }
    end
  end

  defp read_submission(cache, uri) do
    row = Subject.fetch(cache, :subm, uri)

    %{
      stop: Rdf.parse_datetime(Rdf.val(row, "stop")),
      submitter_name: Rdf.val(row, "submitterName")
    }
  end
end
