defmodule CitationPopulator.Subject do
  @moduledoc """
  The per-subject field reads of a citable subject, over one subject or a
  whole batch of them.

  Everything a subject's References needs that is *its own* — rather than
  shared reference data like specs, stations or agents — is read here, one
  SPARQL round-trip per field group. Read a subject at a time, as `fetch/3`
  does on a cache miss, and that is six round-trips per subject, which at a
  few million subjects is what a population pass spends nearly all its time
  on. So `load/3` reads a whole batch of subjects up front instead, binding
  them with `VALUES ?s`: six round-trips for five hundred subjects rather
  than for one.

  The results are seeded into the shared [`Cache`](`CitationPopulator.Cache`)
  under `{group, subject_uri}` keys, which is where `CitationPopulator.Reader`
  and `CitationPopulator.Licence` look for them, so a batch is invisible to
  them beyond being fast. A subject that was never prefetched still reads
  correctly, one round-trip at a time — the batch and the single-subject read
  run the same query with a different number of URIs bound.

  Prefetched entries are dropped again with `forget/2` once the batch has
  been processed: unlike the reference data around them they serve exactly
  one subject, so keeping them would grow the cache by the whole triplestore
  over a run.

  Each group is one of three shapes: `:row` for a group of single-valued
  fields (the first solution per subject, as a `LIMIT 1` read of that subject
  would have seen), `:value` for a lone single-valued field, and `:values`
  for a multi-valued one, whose `?v` bindings are collected per subject in
  query order.
  """

  alias CitationPopulator.{Cache, Rdf, Vocab}

  @doc """
  Reads every field group for `subjects` (`{uri, class}` pairs) and seeds
  them into the cache. One query per group per class present in the batch.
  """
  def load(cache, subjects, derived_graph) do
    by_kind = Enum.group_by(subjects, &kind(elem(&1, 1)), &elem(&1, 0))
    data = Map.get(by_kind, :data, [])
    docs = Map.get(by_kind, :doc, [])
    colls = Map.get(by_kind, :collection, [])
    all = data ++ docs ++ colls

    [
      {:data_core, data},
      {:acq, data},
      {:prod, data},
      {:contributors, data},
      {:subm, data ++ docs},
      {:doc_core, docs},
      {:creators, docs},
      {:coll_core, colls},
      {:has_doi, all},
      {:own_licence, all}
    ]
    |> Enum.each(fn {name, uris} -> seed(cache, name, group(name, derived_graph), uris) end)
  end

  @groups [
    :data_core,
    :doc_core,
    :coll_core,
    :acq,
    :prod,
    :contributors,
    :subm,
    :creators,
    :has_doi,
    :own_licence
  ]

  @doc "Drops a batch's prefetched entries once its subjects are done."
  def forget(cache, subjects) do
    Enum.each(subjects, fn {uri, _class} ->
      Enum.each(@groups, &Cache.delete(cache, {&1, uri}))
    end)
  end

  @doc """
  A subject's field group: the prefetched value, or a read of just this
  subject when it was not part of a batch.

  `:own_licence` needs `derived_graph`, since its query has to exclude that
  graph — the populator writes licence triples there itself, so an
  unrestricted read would find its own output.
  """
  def fetch(cache, name, uri, derived_graph \\ nil) do
    case Cache.get(cache, {name, uri}) do
      {:ok, value} -> value
      :miss -> read(group(name, derived_graph), [uri]) |> Map.fetch!(uri)
    end
  end

  @doc """
  Which of `uris` the derived graph already holds triples for.

  Asked per batch rather than by loading every materialized subject up front:
  that read was a `DISTINCT` over the whole derived graph, which the cursor
  paging re-scanned and re-sorted once per page, so it grew with the graph
  until it dominated a resumed run's startup — and it held every subject URI
  in memory besides. Bound to a batch it is a few hundred index lookups.
  """
  def materialized([], _derived_graph), do: MapSet.new()

  def materialized(uris, derived_graph) do
    """
    SELECT DISTINCT ?s WHERE {
      VALUES ?s { #{Enum.map_join(uris, " ", &"<#{&1}>")} }
      GRAPH <#{derived_graph}> { ?s ?p ?o }
    }
    """
    |> Rdf.select()
    |> MapSet.new(&Rdf.val(&1, "s"))
  end

  @doc """
  Indexes a group's result rows by their `?s` binding, one entry per URI in
  `uris` — including the ones the query returned nothing for, which get the
  shape's empty value. The counterpart of `Rdf.select_one/1`'s `LIMIT 1` and
  `Rdf.values/2` over a batch of subjects at once.
  """
  def index(rows, shape, uris) do
    found = collect(rows, shape)
    Map.new(uris, fn uri -> {uri, Map.get(found, uri, empty(shape))} end)
  end

  # Mirrors References.build/4's dispatch, including its treatment of an
  # unknown class as a data object.
  defp kind(class) do
    cond do
      class == Vocab.collection_class() -> :collection
      class == Vocab.doc_object_class() -> :doc
      true -> :data
    end
  end

  defp seed(_cache, _name, _group, []), do: :ok

  defp seed(cache, name, group, uris) do
    Enum.each(read(group, uris), fn {uri, value} -> Cache.put(cache, {name, uri}, value) end)
  end

  defp read({shape, pattern}, uris) do
    values = Enum.map_join(uris, " ", &"<#{&1}>")

    """
    SELECT * WHERE {
      VALUES ?s { #{values} }
    #{pattern}}
    """
    |> Rdf.select()
    |> index(shape, uris)
  end

  defp collect(rows, :values) do
    rows
    |> Enum.reverse()
    |> Enum.reduce(%{}, fn row, acc ->
      Map.update(acc, Rdf.val(row, "s"), [Rdf.val(row, "v")], &[Rdf.val(row, "v") | &1])
    end)
    |> Map.new(fn {uri, values} -> {uri, Enum.uniq(values)} end)
  end

  defp collect(rows, shape) do
    Enum.reduce(rows, %{}, fn row, acc ->
      value = if shape == :value, do: Rdf.val(row, "v"), else: row
      Map.put_new(acc, Rdf.val(row, "s"), value)
    end)
  end

  defp empty(:values), do: []
  defp empty(_row_or_value), do: nil

  defp group(:data_core, _graph) do
    {:row,
     """
       OPTIONAL { ?s cpmeta:hasName ?fileName }
       OPTIONAL { ?s cpmeta:hasSizeInBytes ?size }
       OPTIONAL { ?s cpmeta:hasDoi ?doi }
       OPTIONAL { ?s cpmeta:hasKeywords ?keywords }
       OPTIONAL { ?s cpmeta:hasObjectSpec ?spec }
       OPTIONAL { ?s cpmeta:hasActualColumnNames ?actualColumns }
       OPTIONAL { ?s dcterms:title ?title }
       OPTIONAL { ?s dcterms:description ?description }
       OPTIONAL { ?s cpmeta:hasStartTime ?startTime }
       OPTIONAL { ?s cpmeta:hasEndTime ?endTime }
     """}
  end

  defp group(:doc_core, _graph) do
    {:row,
     """
       OPTIONAL { ?s cpmeta:hasName ?fileName }
       OPTIONAL { ?s cpmeta:hasDoi ?doi }
       OPTIONAL { ?s cpmeta:hasKeywords ?keywords }
       OPTIONAL { ?s dcterms:title ?title }
     """}
  end

  defp group(:coll_core, _graph) do
    {:row,
     """
       OPTIONAL { ?s dcterms:title ?title }
       OPTIONAL { ?s cpmeta:hasDoi ?doi }
     """}
  end

  defp group(:acq, _graph) do
    {:row,
     """
       ?s cpmeta:wasAcquiredBy ?acq .
       OPTIONAL { ?acq prov:startedAtTime ?start }
       OPTIONAL { ?acq prov:endedAtTime ?stop }
       OPTIONAL { ?acq prov:wasAssociatedWith ?station .
                  OPTIONAL { ?station cpmeta:hasName ?stationName } }
       OPTIONAL { ?acq cpmeta:hasSamplingHeight ?samplingHeight }
       OPTIONAL { ?acq cpmeta:wasPerformedAt ?site .
                  OPTIONAL { ?site cpmeta:hasSpatialCoverage ?siteCov .
                             OPTIONAL { ?siteCov rdfs:label ?siteLocationLabel } } }
       OPTIONAL { ?acq cpmeta:hasSamplingPoint ?sp .
                  OPTIONAL { ?sp rdfs:label ?samplingPointLabel } }
     """}
  end

  defp group(:prod, _graph) do
    {:row,
     """
       ?s cpmeta:wasProducedBy ?prod .
       OPTIONAL { ?prod cpmeta:wasPerformedBy ?creator }
       OPTIONAL { ?prod cpmeta:hasEndTime ?dateTime }
     """}
  end

  # Keyed by the object rather than by its production, so that one query
  # serves the batch; an object's production is not shared with other objects.
  defp group(:contributors, _graph) do
    {:values,
     """
       ?s cpmeta:wasProducedBy ?prod .
       ?prod cpmeta:wasParticipatedInBy ?v
     """}
  end

  defp group(:subm, _graph) do
    {:row,
     """
       ?s cpmeta:wasSubmittedBy ?subm .
       OPTIONAL { ?subm prov:endedAtTime ?stop }
       OPTIONAL { ?subm prov:wasAssociatedWith ?org .
                  OPTIONAL { ?org cpmeta:hasName ?submitterName } }
     """}
  end

  defp group(:creators, _graph) do
    {:values,
     """
       ?s dcterms:creator ?v
     """}
  end

  defp group(:has_doi, _graph) do
    {:values,
     """
       ?s cpmeta:hasDoi ?v
     """}
  end

  defp group(:own_licence, derived_graph) do
    {:value,
     """
       GRAPH ?g { ?s dcterms:license ?v }
       FILTER(?g != <#{derived_graph}>)
     """}
  end
end
