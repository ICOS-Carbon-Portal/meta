defmodule Keywords.Materializer do
  @moduledoc """
  Materializes `hasKeyword` (singular) triples onto data and document objects and
  writes them into a dedicated derived named graph.

  This replaces the lookup support that the deleted "magic index" used to provide.
  The index treated an object as carrying a keyword if it appeared in any of three
  sources, unioned together: the object's own keywords, its spec's (`hasObjectSpec`),
  and the spec's project's (`hasAssociatedProject`). With a remote SPARQL backend we
  cannot expand that inherited union at query-evaluation time, so we materialize one
  `hasKeyword` triple per distinct inherited keyword onto each object ahead of time
  instead.

  The keywords are read from the singular `hasOwnKeyword` triples that
  `Keywords.Splitter` produced out of the legacy comma-separated `hasKeywords`
  literals; nothing is parsed or split here.

  The derived graph is treated as a cache: `materialize_all/2` clears and rebuilds it,
  making each run idempotent.
  """

  require Logger
  alias Keywords.Virtuoso

  @cpmeta "http://meta.icos-cp.eu/ontologies/cpmeta/"
  @data_object @cpmeta <> "DataObject"
  @document_object @cpmeta <> "DocumentObject"
  @has_keyword @cpmeta <> "hasKeyword"
  @has_own_keyword @cpmeta <> "hasOwnKeyword"
  @has_object_spec @cpmeta <> "hasObjectSpec"
  @has_associated_project @cpmeta <> "hasAssociatedProject"

  @write_batch_size 1000

  # Pagination guards against the triplestore silently truncating large result sets:
  # Virtuoso's /sparql endpoint caps rows at ResultSetMaxRows (commonly 10000) and can
  # time out heavy queries, in both cases returning a partial result with no error. We
  # therefore keep every individual query's result well under any such cap, in two
  # phases: enumerate object IRIs page by page, then fetch keywords for bounded batches
  # of those objects.
  #
  # Enumeration uses keyset (seek) pagination rather than LIMIT/OFFSET: Virtuoso refuses
  # an ORDER BY whose sorted window (OFFSET + LIMIT) exceeds MaxSortedTopRows (10000 by
  # default, error SR353), so deep OFFSETs fail outright. Carrying a `?obj > cursor`
  # filter instead keeps every page's sort to just @object_page_size rows. Both page
  # sizes must stay below the configured caps.
  @object_page_size 5000
  @keyword_batch_size 1000

  @doc "Clears the derived graph and rebuilds it, returning the number of triples written."
  def materialize_all(repo, derived_graph) do
    Logger.info("Keyword materialization started (graph #{derived_graph})")
    Logger.info("Clearing derived graph #{derived_graph}")
    Virtuoso.update(repo, "CLEAR GRAPH #{Virtuoso.iri_ref(derived_graph)}")
    Logger.info("Derived graph cleared; collecting keywords")

    keywords_by_obj = collect_object_keywords(repo)
    total = Enum.sum(for {_obj, kws} <- keywords_by_obj, do: MapSet.size(kws))

    Logger.info(
      "Collected keywords for #{map_size(keywords_by_obj)} objects " <>
        "(#{total} keyword triples to write)"
    )

    written = write_in_batches(repo, derived_graph, keywords_by_obj)
    Logger.info("Keyword materialization finished, wrote #{written} triples")
    written
  end

  # For every data/document object, the union of its own `hasOwnKeyword` keywords, its
  # spec's and the spec's project's (the same three sources the magic index unioned),
  # de-duplicated per object.
  #
  # Done in two paginated phases so that no single SPARQL result set can exceed the
  # triplestore's row cap (or time out) and be silently truncated: first enumerate the
  # object IRIs page by page, then fetch keywords for bounded batches of those objects.
  defp collect_object_keywords(repo) do
    objects = list_objects(repo)

    Logger.info(
      "Enumerated #{length(objects)} data/document objects; " <>
        "fetching keywords in batches of #{@keyword_batch_size}"
    )

    {keywords_by_obj, _done} =
      for batch <- Enum.chunk_every(objects, @keyword_batch_size), reduce: {%{}, 0} do
        {acc, done} ->
          acc = fetch_keywords_for_batch(repo, batch, acc)
          done = done + length(batch)

          Logger.info(
            "Fetched keywords for #{done}/#{length(objects)} objects " <>
              "(#{map_size(acc)} have keywords so far)"
          )

          {acc, done}
      end

    Logger.info(
      "Keyword collection finished: #{map_size(keywords_by_obj)} of " <>
        "#{length(objects)} objects carry keywords"
    )

    keywords_by_obj
  end

  # All data/document object IRIs, read page by page with keyset (seek) pagination: each
  # page asks for the next @object_page_size objects ordered after the previous page's
  # last IRI. Unlike LIMIT/OFFSET this keeps every page's sorted window small (avoiding
  # Virtuoso's MaxSortedTopRows limit) and never silently caps the enumeration.
  defp list_objects(repo) do
    Logger.info("Enumerating data/document objects")
    # "" sorts before every IRI
    list_objects(repo, "", [])
  end

  defp list_objects(repo, cursor, acc) do
    query = """
    SELECT DISTINCT ?obj WHERE {
      ?obj a ?t .
      FILTER(?t IN (<#{@data_object}>, <#{@document_object}>))
      FILTER(STR(?obj) > #{Virtuoso.plain_literal(cursor)})
    }
    ORDER BY STR(?obj)
    LIMIT #{@object_page_size}
    """

    rows = Virtuoso.select(repo, query)
    page = for row <- rows, obj = Virtuoso.iri(row, "obj"), do: obj
    acc = acc ++ page
    Logger.info("Enumerated #{length(acc)} objects so far")

    if length(rows) == @object_page_size do
      list_objects(repo, List.last(page) || cursor, acc)
    else
      acc
    end
  end

  # Fetches own/spec/project `hasOwnKeyword` keywords for a bounded batch of objects in
  # one query.
  defp fetch_keywords_for_batch(repo, batch, acc) do
    values = Enum.map_join(batch, " ", &Virtuoso.iri_ref/1)

    query = """
    SELECT ?obj ?keyword WHERE {
      VALUES ?obj { #{values} }
      {
        ?obj <#{@has_own_keyword}> ?keyword .
      } UNION {
        ?obj <#{@has_object_spec}> ?spec .
        ?spec <#{@has_own_keyword}> ?keyword .
      } UNION {
        ?obj <#{@has_object_spec}> ?spec .
        ?spec <#{@has_associated_project}> ?proj .
        ?proj <#{@has_own_keyword}> ?keyword .
      }
    }
    """

    for row <- Virtuoso.select(repo, query),
        obj = Virtuoso.iri(row, "obj"),
        keyword = Virtuoso.literal(row, "keyword"),
        kw = String.trim(keyword),
        kw != "",
        reduce: acc do
      acc -> Map.update(acc, obj, MapSet.new([kw]), &MapSet.put(&1, kw))
    end
  end

  defp write_in_batches(repo, derived_graph, keywords_by_obj) do
    triples =
      for {obj, kws} <- keywords_by_obj, kw <- kws do
        "#{Virtuoso.iri_ref(obj)} <#{@has_keyword}> #{Virtuoso.typed_literal(kw)} ."
      end

    total =
      for batch <- Enum.chunk_every(triples, @write_batch_size), reduce: 0 do
        total ->
          Virtuoso.update(repo, """
          INSERT DATA { GRAPH #{Virtuoso.iri_ref(derived_graph)} {
          #{Enum.join(batch, "\n")}
          } }
          """)

          total = total + length(batch)
          Logger.info("Wrote batch of #{length(batch)} triples (total #{total} written)")
          total
      end

    Logger.info("Finished writing #{total} triples to #{derived_graph}")
    total
  end
end
