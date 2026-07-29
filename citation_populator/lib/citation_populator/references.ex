defmodule CitationPopulator.References do
  @moduledoc """
  Computes the References JSON map for a citable subject — the port of
  LiveCitationMaker.getCitationInfo / getItemCitationInfo plus the relevant
  parts of StaticObjectReader and CollectionReader.

  Subjects without a DOI are assembled structurally from triplestore data
  and returned complete. DOI subjects are returned as a deferred job: the
  structural part (refs_base) is computed here, and the DataCiteQueue later
  merges in the DataCite-fetched citation strings (HTML/BibTeX/RIS) and DOI
  metadata via complete_deferred/3. Like the Scala materializer, a DataCite
  failure skips the subject's DataCite-dependent triples rather than
  materializing placeholder text.
  """

  alias CitationPopulator.{
    Agent,
    Cache,
    Citation,
    Columns,
    DataCite,
    Envri,
    Licence,
    Rdf,
    Reader,
    Structured,
    Subject,
    Vocab
  }

  import CitationPopulator.Util, only: [put_opt: 3, non_empty: 1, parse_comma_sep: 1]

  @doc """
  Returns:
    {:ok, refs}        — full references computed (no DataCite needed)
    {:deferred, job}   — DOI subject: job = %{mode, doi, refs_base} for the
                         DataCiteQueue; mode :full merges the DataCite bundle
                         into refs_base, mode :citation_only (subjects outside
                         the known URI prefixes, untitled collections) only
                         materializes the DOI citation string
    :none              — nothing computable for this subject
    {:error, reason}   — skip the subject
  """
  def build(uri, class, derived_graph, context) do
    cond do
      class == Vocab.collection_class() -> build_collection(uri, derived_graph, context)
      class == Vocab.doc_object_class() -> build_object(uri, :doc, derived_graph, context)
      true -> build_object(uri, :data, derived_graph, context)
    end
  end

  defp build_object(uri, kind, graph, context) do
    case Envri.object_envri(uri) do
      # Mirrors Scala: when the subject is not under a known object prefix the
      # CitableItem lookup yields nothing and only a DOI citation is written.
      nil -> doi_citation_only(uri, context.cache, core_group(kind))
      envri when kind == :doc -> build_doc(uri, envri, graph, context)
      envri -> build_data(uri, envri, graph, context)
    end
  end

  defp build_data(uri, envri, _graph, context) do
    obj = Reader.data_object(uri, context.cache)
    doi = DataCite.parse_doi(obj.doi_raw)

    columns =
      if obj.spec.dataset_type == :station_time_series,
        do: Columns.for_object(context.cache, obj.spec.dataset, obj.actual_columns_json)

    obj = obj |> Map.put(:columns, columns) |> Map.put(:cache, context.cache)
    pid = data_object_pid(obj, envri)
    pid_url = Envri.pid_url(obj.doi_raw, pid)

    cit_info =
      case envri do
        :sites -> Citation.sites_citation(obj, pid_url)
        _ -> Citation.icos_citation(obj, envri, pid_url)
      end

    licence =
      Licence.resolve(
        context.cache,
        obj.own_licence_uri,
        obj.spec.uri,
        obj.spec.project_uri,
        envri
      )

    keywords = parse_comma_sep(obj.keywords_raw)

    structured = structured_input(obj, cit_info, licence, keywords, pid, obj.l3.description)

    refs =
      base_refs(cit_info, structured, licence, keywords)
      |> put_opt("temporalCoverageDisplay", cit_info.temp_cov)
      |> put_opt("acknowledgements", non_empty(acknowledgements(obj)))

    finish(refs, doi)
  end

  defp build_doc(uri, envri, _graph, context) do
    obj = Reader.doc_object(uri, context.cache) |> Map.put(:cache, context.cache)
    doi = DataCite.parse_doi(obj.doi_raw)

    authors = Agent.read_contributors(context.cache, obj.creator_uris)
    pid = doc_object_pid(obj, envri)
    pid_url = Envri.pid_url(obj.doi_raw, pid)
    cit_info = Citation.doc_citation(obj, envri, authors, pid_url)
    licence = Licence.resolve(context.cache, obj.own_licence_uri, nil, nil, envri)
    keywords = parse_comma_sep(obj.keywords_raw)

    # Documents have no temporal coverage, so the BibTeX/RIS title tag
    # (title + coverage) is absent — same as in Scala.
    structured = structured_input(obj, cit_info, licence, keywords, pid, nil, authors)
    refs = base_refs(cit_info, structured, licence, keywords, authors)

    finish(refs, doi)
  end

  defp build_collection(uri, _graph, context) do
    case Envri.collection_envri(uri) do
      nil ->
        doi_citation_only(uri, context.cache, :coll_core)

      envri ->
        coll = Reader.collection(uri, context.cache)
        doi = DataCite.parse_doi(coll.doi_raw)

        if coll.title == nil do
          # Scala requires the collection title and falls back to the bare
          # DOI citation when the collection cannot be read.
          doi_citation_only(uri, context.cache, :coll_core)
        else
          # Collections have no spec/project, so the licence chain falls
          # straight to the ENVRI default, mirroring LiveCitationMaker's
          # getLicence for subjects with no hasObjectSpec.
          licence = Licence.resolve(context.cache, coll.own_licence_uri, nil, nil, envri)
          finish(%{"title" => coll.title, "licence" => licence}, doi)
        end
    end
  end

  defp finish(refs, nil), do: {:ok, refs}
  defp finish(refs, doi), do: {:deferred, %{mode: :full, doi: doi, refs_base: refs}}

  defp structured_input(obj, cit_info, licence, keywords, pid, note, authors \\ nil) do
    %{
      pid_url: obj |> Map.get(:doi_raw) |> Envri.pid_url(pid),
      file_name: obj.file_name,
      hash_id: obj.hash_id,
      authors: non_empty(authors || cit_info.authors),
      title: cit_info.title,
      temp_cov: cit_info.temp_cov,
      year: cit_info.year,
      note: if(note && obj.spec.dataset_type == :spatio_temporal, do: note),
      keywords: keywords,
      publisher: obj.subm.submitter_name,
      licence_url: licence["url"],
      doi_raw: obj.doi_raw,
      pid: pid
    }
  end

  defp base_refs(cit_info, structured, licence, keywords, authors \\ nil) do
    %{"licence" => licence}
    |> put_opt("citationString", cit_info.cit_text)
    |> Map.put("citationBibTex", Structured.to_bibtex(structured))
    |> Map.put("citationRis", Structured.to_ris(structured))
    |> put_opt("keywords", keywords)
    |> put_opt("authors", non_empty(authors || cit_info.authors))
    |> put_opt("title", cit_info.title)
  end

  @doc """
  Completes a deferred DataCiteQueue job: fetches the DataCite data and
  returns the final References map (:full) or just the citation string
  (:citation_only). All lookups must succeed (the Scala dataCiteReady gate).
  """
  def complete_deferred(:citation_only, doi, _refs_base, queue) do
    with {:ok, citation} <- DataCite.fetch_citation(doi, :html, queue) do
      {:ok, %{"citationString" => citation}}
    else
      {:error, reason} ->
        {:error, "DataCite citation for #{DataCite.doi_to_string(doi)} failed: #{reason}"}
    end
  end

  def complete_deferred(:full, doi, refs_base, queue) do
    with {:ok, bundle} <- fetch_doi_bundle(doi, queue) do
      {:ok,
       refs_base
       |> Map.put("citationString", bundle.html)
       |> Map.put("citationBibTex", bundle.bibtex)
       |> Map.put("citationRis", bundle.ris)
       |> Map.put("doi", bundle.meta)}
    end
  end

  defp fetch_doi_bundle(doi, queue) do
    with {:ok, html} <- DataCite.fetch_citation(doi, :html, queue),
         {:ok, bibtex} <- DataCite.fetch_citation(doi, :bibtex, queue),
         {:ok, ris} <- DataCite.fetch_citation(doi, :ris, queue),
         {:ok, meta} <- DataCite.fetch_doi_meta(doi, queue) do
      {:ok, %{html: html, bibtex: bibtex, ris: ris, meta: meta}}
    else
      {:error, reason} ->
        {:error, "DataCite lookup for #{DataCite.doi_to_string(doi)} failed: #{reason}"}
    end
  end

  defp doi_citation_only(uri, cache, core_group) do
    raw = cache |> Subject.fetch(core_group, uri) |> Rdf.val("doi")

    case DataCite.parse_doi(raw) do
      nil -> :none
      doi -> {:deferred, %{mode: :citation_only, doi: doi, refs_base: %{}}}
    end
  end

  defp core_group(:data), do: :data_core
  defp core_group(:doc), do: :doc_core

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

      station_fundings(obj.cache, obj.acq.station_uri)
      |> Enum.filter(&funding_overlaps?(&1, interval))
      |> Enum.sort_by(fn f ->
        {date_iso(f.stop, "9999-12-31"), date_iso(f.start, "0000-01-01"), f.funder_name}
      end)
      |> Enum.map(&acknowledgement/1)
    else
      []
    end
  end

  # A station's fundings are shared by every object acquired there, so read
  # and shape them once per station per run; only the per-object interval
  # overlap filtering above stays live.
  defp station_fundings(cache, station_uri) do
    Cache.fetch(cache, {:funding, station_uri}, fn ->
      Rdf.select("""
      SELECT * WHERE {
        <#{station_uri}> cpmeta:hasFunding ?funding .
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
    end)
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
