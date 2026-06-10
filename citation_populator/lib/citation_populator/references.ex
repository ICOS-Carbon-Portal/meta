defmodule CitationPopulator.References do
  @moduledoc """
  Computes the References JSON map for a citable subject — the port of
  LiveCitationMaker.getCitationInfo / getItemCitationInfo plus the relevant
  parts of StaticObjectReader and CollectionReader.

  DOI-backed subjects get their citation strings (HTML/BibTeX/RIS) and DOI
  metadata from DataCite; everything else is assembled structurally from
  triplestore data. Like the Scala materializer (which defers DOI subjects
  until all DataCite lookups succeed and skips them on failure), a DataCite
  failure skips the subject rather than materializing placeholder text.
  """

  require Logger

  alias CitationPopulator.{
    Agent,
    Citation,
    Columns,
    DataCite,
    Envri,
    Licence,
    Rdf,
    Reader,
    Structured,
    Vocab
  }

  import CitationPopulator.Util, only: [put_opt: 3, non_empty: 1, parse_comma_sep: 1]

  @doc """
  Returns:
    {:ok, refs}            — full references computed
    {:citation_only, cit}  — subject outside the known object/collection URI
                             prefixes (or an untitled collection); only the
                             DOI citation could be computed
    :none                  — nothing computable for this subject
    {:error, reason}       — DataCite failure etc.; skip the subject
  """
  def build(uri, class, derived_graph) do
    cond do
      class == Vocab.collection_class() -> build_collection(uri)
      class == Vocab.doc_object_class() -> build_object(uri, :doc, derived_graph)
      true -> build_object(uri, :data, derived_graph)
    end
  end

  defp build_object(uri, kind, graph) do
    case Envri.object_envri(uri) do
      # Mirrors Scala: when the subject is not under a known object prefix the
      # CitableItem lookup yields nothing and only a DOI citation is written.
      nil -> doi_citation_only(uri)
      envri when kind == :doc -> build_doc(uri, envri, graph)
      envri -> build_data(uri, envri, graph)
    end
  end

  defp build_data(uri, envri, graph) do
    obj = Reader.data_object(uri)
    doi = DataCite.parse_doi(obj.doi_raw)

    with {:ok, bundle} <- fetch_doi_bundle(doi) do
      columns =
        if obj.spec.dataset_type == :station_time_series,
          do: Columns.for_object(obj.spec.dataset, obj.actual_columns_json)

      obj = Map.put(obj, :columns, columns)
      pid = data_object_pid(obj, envri)
      pid_url = Envri.pid_url(obj.doi_raw, pid)

      cit_info =
        case envri do
          :sites -> Citation.sites_citation(obj, pid_url)
          _ -> Citation.icos_citation(obj, envri, pid_url)
        end

      licence = Licence.resolve(uri, obj.spec.uri, obj.spec.project_uri, envri, graph)
      keywords = parse_comma_sep(obj.keywords_raw)

      structured = %{
        pid_url: pid_url,
        file_name: obj.file_name,
        hash_id: obj.hash_id,
        authors: non_empty(cit_info.authors),
        title: cit_info.title,
        temp_cov: cit_info.temp_cov,
        year: cit_info.year,
        note: if(obj.spec.dataset_type == :spatio_temporal, do: obj.l3.description),
        keywords: keywords,
        publisher: obj.subm.submitter_name,
        licence_url: licence["url"],
        doi_raw: obj.doi_raw,
        pid: pid
      }

      refs =
        %{"licence" => licence}
        |> put_opt("citationString", bundle[:html] || cit_info.cit_text)
        |> Map.put("citationBibTex", bundle[:bibtex] || Structured.to_bibtex(structured))
        |> Map.put("citationRis", bundle[:ris] || Structured.to_ris(structured))
        |> put_opt("doi", bundle[:meta])
        |> put_opt("keywords", keywords)
        |> put_opt("authors", non_empty(cit_info.authors))
        |> put_opt("title", cit_info.title)
        |> put_opt("temporalCoverageDisplay", cit_info.temp_cov)
        |> put_opt("acknowledgements", non_empty(acknowledgements(obj)))

      {:ok, refs}
    end
  end

  defp build_doc(uri, envri, graph) do
    obj = Reader.doc_object(uri)
    doi = DataCite.parse_doi(obj.doi_raw)

    with {:ok, bundle} <- fetch_doi_bundle(doi) do
      authors = Agent.read_contributors(obj.creator_uris)
      pid = doc_object_pid(obj, envri)
      pid_url = Envri.pid_url(obj.doi_raw, pid)
      cit_info = Citation.doc_citation(obj, envri, authors, pid_url)
      licence = Licence.resolve(uri, nil, nil, envri, graph)
      keywords = parse_comma_sep(obj.keywords_raw)

      structured = %{
        pid_url: pid_url,
        file_name: obj.file_name,
        hash_id: obj.hash_id,
        authors: non_empty(authors),
        # documents have no temporal coverage, so the BibTeX/RIS title tag
        # (title + coverage) is absent — same as in Scala
        title: cit_info.title,
        temp_cov: nil,
        year: cit_info.year,
        note: nil,
        keywords: keywords,
        publisher: obj.subm.submitter_name,
        licence_url: licence["url"],
        doi_raw: obj.doi_raw,
        pid: pid
      }

      refs =
        %{"licence" => licence}
        |> put_opt("citationString", bundle[:html] || cit_info.cit_text)
        |> Map.put("citationBibTex", bundle[:bibtex] || Structured.to_bibtex(structured))
        |> Map.put("citationRis", bundle[:ris] || Structured.to_ris(structured))
        |> put_opt("doi", bundle[:meta])
        |> put_opt("keywords", keywords)
        |> put_opt("authors", non_empty(authors))
        |> put_opt("title", cit_info.title)

      {:ok, refs}
    end
  end

  defp build_collection(uri) do
    case Envri.collection_envri(uri) do
      nil ->
        doi_citation_only(uri)

      _envri ->
        coll = Reader.collection(uri)
        doi = DataCite.parse_doi(coll.doi_raw)

        if coll.title == nil do
          # Scala requires the collection title and falls back to the bare
          # DOI citation when the collection cannot be read.
          doi_citation_only(uri)
        else
          with {:ok, bundle} <- fetch_doi_bundle(doi) do
            refs =
              %{"title" => coll.title}
              |> put_opt("citationString", (bundle || %{})[:html])
              |> put_opt("citationBibTex", (bundle || %{})[:bibtex])
              |> put_opt("citationRis", (bundle || %{})[:ris])
              |> put_opt("doi", (bundle || %{})[:meta])

            {:ok, refs}
          end
        end
    end
  end

  # All four DataCite lookups must succeed before a DOI subject is
  # materialized (the Scala dataCiteReady gate); nil DOI means no bundle.
  defp fetch_doi_bundle(nil), do: {:ok, nil}

  defp fetch_doi_bundle(doi) do
    with {:ok, html} <- DataCite.fetch_citation(doi, :html),
         {:ok, bibtex} <- DataCite.fetch_citation(doi, :bibtex),
         {:ok, ris} <- DataCite.fetch_citation(doi, :ris),
         {:ok, meta} <- DataCite.fetch_doi_meta(doi) do
      {:ok, %{html: html, bibtex: bibtex, ris: ris, meta: meta}}
    else
      {:error, reason} ->
        {:error, "DataCite lookup for #{DataCite.doi_to_string(doi)} failed: #{reason}"}
    end
  end

  defp doi_citation_only(uri) do
    raw = Rdf.values("SELECT ?doi WHERE { <#{uri}> cpmeta:hasDoi ?doi }", "doi") |> List.first()

    case DataCite.parse_doi(raw) do
      nil ->
        :none

      doi ->
        case DataCite.fetch_citation(doi, :html) do
          {:ok, cit} ->
            {:citation_only, cit}

          {:error, reason} ->
            {:error, "DataCite citation for #{DataCite.doi_to_string(doi)} failed: #{reason}"}
        end
    end
  end

  defp data_object_pid(obj, envri) do
    prefix = Envri.handle_prefix(envri)

    if obj.has_size and obj.spec.format != Vocab.wdcgg_format() and prefix,
      do: "#{prefix}/#{obj.hash_id}"
  end

  defp doc_object_pid(obj, envri) do
    prefix = Envri.handle_prefix(envri)
    if obj.subm.stop && prefix, do: "#{prefix}/#{obj.hash_id}"
  end

  # Port of CitationMaker.getFundingObjects + getFundingAcknowledgements:
  # station fundings overlapping the acquisition interval (compared at CET
  # noon of the funding dates), as acknowledgement strings.
  defp acknowledgements(obj) do
    if obj.spec.dataset_type == :station_time_series and obj.acq.station_uri do
      interval = if obj.acq.start && obj.acq.stop, do: {obj.acq.start, obj.acq.stop}

      Rdf.select("""
      SELECT * WHERE {
        <#{obj.acq.station_uri}> cpmeta:hasFunding ?funding .
        ?funding cpmeta:hasFunder ?funder .
        ?funder cpmeta:hasName ?funderName .
        OPTIONAL { ?funding cpmeta:awardTitle ?awardTitle }
        OPTIONAL { ?funding cpmeta:awardNumber ?awardNumber }
        OPTIONAL { ?funding cpmeta:hasStartDate ?start }
        OPTIONAL { ?funding cpmeta:hasEndDate ?stop }
      }
      """)
      |> Enum.uniq_by(&Rdf.val(&1, "funding"))
      |> Enum.map(fn r ->
        %{
          funder_name: Rdf.val(r, "funderName"),
          award_title: Rdf.val(r, "awardTitle"),
          award_number: Rdf.val(r, "awardNumber"),
          start: Rdf.parse_date(Rdf.val(r, "start")),
          stop: Rdf.parse_date(Rdf.val(r, "stop"))
        }
      end)
      |> Enum.filter(&funding_overlaps?(&1, interval))
      |> Enum.sort_by(fn f ->
        {date_iso(f.stop, "9999-12-31"), date_iso(f.start, "0000-01-01"), f.funder_name}
      end)
      |> Enum.map(&acknowledgement/1)
    else
      []
    end
  end

  defp funding_overlaps?(_funding, nil), do: true

  defp funding_overlaps?(f, {acq_start, acq_stop}) do
    (f.start == nil or DateTime.compare(acq_stop, cet_noon(f.start)) == :gt) and
      (f.stop == nil or DateTime.compare(acq_start, cet_noon(f.stop)) == :lt)
  end

  # toCETnoon: noon at fixed UTC+1, i.e. 11:00 UTC of the same date.
  defp cet_noon(date), do: DateTime.new!(date, ~T[11:00:00])

  defp date_iso(nil, default), do: default
  defp date_iso(date, _default), do: Date.to_iso8601(date)

  defp acknowledgement(f) do
    grant =
      case {f.award_title, f.award_number} do
        {nil, nil} -> ""
        {title, nil} -> " #{title}"
        {nil, number} -> " #{number}"
        {title, number} -> " #{title} (#{number})"
      end

    "Work was funded by grant#{grant} from #{f.funder_name}"
  end
end
