defmodule Keywords.Splitter do
  @moduledoc """
  Splits every `hasKeywords` (plural, comma-separated) literal in the triplestore into one
  `hasOwnKeyword` (singular) triple per distinct keyword on the very same subject, in the
  very same named graph.

  Unlike `Keywords.Materializer` this does no inheritance: it neither restricts subjects to
  data/document objects nor unions in a spec's or project's keywords. It simply expands
  whatever `?s hasKeywords "a, b"` triples exist anywhere in the store into
  `?s hasOwnKeyword "a"`, `?s hasOwnKeyword "b"` on the same `?s`.

  The split is a migration, not a cache: nothing is written to a derived graph and no graph
  is ever cleared. Each subject is handled write-verify(-delete), one bounded batch at a
  time:

    1. write the singular triples into the source graph,
    2. read them back from the store and confirm every expected keyword is present,
    3. if `delete_source_triples?` is on, only then delete the `hasKeywords` triple(s) they
       came from.

  The deletion is off by default, which makes the pass purely additive: the `hasKeywords`
  literals stay in place alongside their singular counterparts. With it on, a batch whose
  read-back does not confirm keeps its `hasKeywords` triples anyway, so an interrupted or
  partially failing run never loses keywords, and re-running picks up exactly what is left.
  Re-adding an already-present `hasOwnKeyword` triple is a no-op in RDF, which makes the
  whole pass idempotent either way.

  A `hasKeywords` literal that parses to no keywords at all (empty, or only separators) has
  no singular counterpart to confirm, so it is reported and left untouched rather than
  deleted without replacement.
  """

  require Logger
  alias Keywords.Virtuoso

  @has_keywords "<http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords>"
  @has_own_keyword "<http://meta.icos-cp.eu/ontologies/cpmeta/hasOwnKeyword>"

  # Pagination guards against the triplestore silently truncating large result sets: see
  # the same note in Keywords.Materializer. Enumeration uses keyset (seek) pagination
  # rather than LIMIT/OFFSET, so that no page's sorted window can exceed Virtuoso's
  # MaxSortedTopRows. Both sizes must stay below the configured caps.
  @subject_page_size 5000
  @subject_batch_size 500

  defmodule Summary do
    @moduledoc "What one run of the split did, and what it could not do."

    defstruct subjects: 0,
              written: 0,
              deleted: 0,
              unconfirmed: [],
              empties: [],
              deletion_failures: []

    def merge(one, other) do
      %Summary{
        subjects: one.subjects + other.subjects,
        written: one.written + other.written,
        deleted: one.deleted + other.deleted,
        unconfirmed: one.unconfirmed ++ other.unconfirmed,
        empties: one.empties ++ other.empties,
        deletion_failures: one.deletion_failures ++ other.deletion_failures
      }
    end

    def to_string(summary) do
      "#{summary.subjects} subjects split, #{summary.written} hasOwnKeyword written, " <>
        "#{summary.deleted} hasKeywords deleted, #{length(summary.unconfirmed)} unconfirmed, " <>
        "#{length(summary.deletion_failures)} deletion failures, " <>
        "#{length(summary.empties)} without parseable keyword"
    end
  end

  @doc "Splits every `hasKeywords` literal in the store, returning a `Summary` of the run."
  def split_all(repo, delete_source_triples?) do
    Logger.info(
      if delete_source_triples? do
        "Keyword splitting started, deleting hasKeywords triples"
      else
        "Keyword splitting started, keeping hasKeywords triples"
      end
    )

    summary = split_pages(repo, delete_source_triples?, "", %Summary{}, 0)
    Logger.info("Keyword splitting finished: #{Summary.to_string(summary)}")

    if summary.unconfirmed != [] do
      Logger.warning(
        "#{length(summary.unconfirmed)} subjects had hasOwnKeyword triples that could not be " <>
          "confirmed after the write: " <>
          (summary.unconfirmed |> Enum.take(20) |> Enum.join(", "))
      )
    end

    if summary.empties != [] do
      Logger.warning(
        "#{length(summary.empties)} subjects have a hasKeywords literal with no parseable " <>
          "keyword and were left untouched: " <>
          (summary.empties |> Enum.take(20) |> Enum.join(", "))
      )
    end

    summary
  end

  defp split_pages(repo, delete?, cursor, summary, done) do
    page = list_subject_page(repo, cursor)

    {summary, done} =
      for batch <- Enum.chunk_every(page, @subject_batch_size), reduce: {summary, done} do
        {summary, done} ->
          summary = Summary.merge(summary, process_batch(repo, delete?, batch))
          done = done + length(batch)
          Logger.info("Processed #{done} subjects: #{Summary.to_string(summary)}")
          {summary, done}
      end

    if length(page) == @subject_page_size do
      split_pages(repo, delete?, List.last(page), summary, done)
    else
      summary
    end
  end

  # One page of subject IRIs carrying `hasKeywords`, read with keyset pagination.
  defp list_subject_page(repo, cursor) do
    query = """
    SELECT DISTINCT ?subj WHERE {
      GRAPH ?g { ?subj #{@has_keywords} ?keywords . }
      FILTER(isIRI(?subj) && STR(?subj) > #{Virtuoso.plain_literal(cursor)})
    }
    ORDER BY STR(?subj)
    LIMIT #{@subject_page_size}
    """

    for row <- Virtuoso.select(repo, query), subj = Virtuoso.iri(row, "subj"), do: subj
  end

  # Write, verify, then delete, for one bounded batch of subjects.
  defp process_batch(repo, delete?, batch) do
    {groups, empties} = fetch_keywords(repo, batch)

    if groups == [] do
      %Summary{empties: empties}
    else
      written = write_keywords(repo, groups)
      {confirmed, unconfirmed} = verify_keywords(repo, delete?, groups)

      {deleted, deletion_failures} =
        if delete?, do: delete_sources(repo, confirmed), else: {0, []}

      %Summary{
        subjects: length(Enum.uniq(for group <- confirmed, do: group.subj)),
        written: written,
        deleted: deleted,
        unconfirmed: Enum.uniq(for group <- unconfirmed, do: group.subj),
        empties: empties,
        deletion_failures: deletion_failures
      }
    end
  end

  # The `hasKeywords` literals of a bounded batch of subjects, per source graph, together
  # with the distinct keywords parsed out of them. The second element lists the subjects
  # whose literals hold no parseable keyword at all.
  defp fetch_keywords(repo, batch) do
    query = """
    SELECT ?subj ?g ?keywords WHERE {
      VALUES ?subj { #{Enum.map_join(batch, " ", &Virtuoso.iri_ref/1)} }
      GRAPH ?g { ?subj #{@has_keywords} ?keywords . }
    }
    """

    # several hasKeywords literals may sit on the same subject in the same graph, and the
    # same subject may occur in more than one graph; each (subject, graph) pair is
    # migrated on its own, within that graph
    acc =
      for row <- Virtuoso.select(repo, query),
          subj = Virtuoso.iri(row, "subj"),
          graph = Virtuoso.iri(row, "g"),
          keywords = Virtuoso.literal(row, "keywords"),
          reduce: %{} do
        acc ->
          {count, kws} = Map.get(acc, {subj, graph}, {0, []})
          Map.put(acc, {subj, graph}, {count + 1, kws ++ parse_comma_sep_list(keywords)})
      end

    groups =
      for {{subj, graph}, {count, kws}} <- acc do
        %{subj: subj, graph: graph, source_count: count, keywords: Enum.uniq(kws)}
      end

    {with_keywords, without} = Enum.split_with(groups, &(&1.keywords != []))

    {with_keywords, Enum.uniq(for group <- without, do: group.subj)}
  end

  # Adds the singular triples to the graph their `hasKeywords` source lives in, one
  # `INSERT DATA` per graph, with the keywords as plain literals.
  defp write_keywords(repo, groups) do
    written =
      for {graph, in_graph} <- Enum.group_by(groups, & &1.graph), reduce: 0 do
        written ->
          triples =
            for group <- in_graph, kw <- group.keywords do
              "#{Virtuoso.iri_ref(group.subj)} #{@has_own_keyword} #{Virtuoso.plain_literal(kw)} ."
            end

          Virtuoso.update(repo, """
          INSERT DATA { GRAPH #{Virtuoso.iri_ref(graph)} {
          #{Enum.join(triples, "\n")}
          } }
          """)

          written + length(triples)
      end

    Logger.debug("Wrote #{written} hasOwnKeyword triples for #{length(groups)} subject/graph pairs")
    written
  end

  # Reads the singular triples back and partitions the groups into those whose every
  # expected keyword is now present in the store, and those where something is missing.
  # Only the former may have their `hasKeywords` source deleted, when deletion is on.
  defp verify_keywords(repo, delete?, groups) do
    # one query per source graph, with the graph IRI concrete, so that read-back is a
    # plain lookup in the same graph the triples were just written to
    found =
      for {graph, in_graph} <- Enum.group_by(groups, & &1.graph), reduce: %{} do
        found ->
          query = """
          SELECT ?subj ?kw WHERE {
            VALUES ?subj { #{Enum.map_join(in_graph, " ", &Virtuoso.iri_ref(&1.subj))} }
            GRAPH #{Virtuoso.iri_ref(graph)} { ?subj #{@has_own_keyword} ?kw . }
          }
          """

          for row <- Virtuoso.select(repo, query),
              subj = Virtuoso.iri(row, "subj"),
              kw = Virtuoso.literal(row, "kw"),
              reduce: found do
            found -> Map.update(found, {subj, graph}, MapSet.new([kw]), &MapSet.put(&1, kw))
          end
      end

    Enum.split_with(groups, fn group ->
      present = Map.get(found, {group.subj, group.graph}, MapSet.new())
      missing = for kw <- group.keywords, not MapSet.member?(present, kw), do: kw

      if missing == [] do
        true
      else
        Logger.warning(
          "Unconfirmed keywords of <#{group.subj}> in graph <#{group.graph}>: " <>
            "#{length(missing)} of #{length(group.keywords)} keywords absent after write " <>
            "(#{missing |> Enum.take(5) |> Enum.join(", ")})" <>
            if(delete?, do: "; keeping its hasKeywords", else: "")
        )

        false
      end
    end)
  end

  # Deletes the `hasKeywords` statements the confirmed keywords came from, one
  # `DELETE ... WHERE` per graph. The statements are matched by subject and predicate, with
  # the literal left as a variable, rather than by their exact term: Virtuoso would not
  # match a typed term we send back to it, and every `hasKeywords` literal of a confirmed
  # subject in that graph was read and split by fetch_keywords anyway.
  #
  # The count returned is what the store actually lost, established by counting the
  # remaining `hasKeywords` of the same subjects afterwards, so that a delete which matches
  # nothing cannot be reported as a success.
  defp delete_sources(repo, confirmed) do
    {deleted, failures} =
      for {graph, in_graph} <- Enum.group_by(confirmed, & &1.graph), reduce: {0, []} do
        {deleted, failures} ->
          subj_filter =
            "FILTER(?subj IN (#{Enum.map_join(in_graph, ", ", &Virtuoso.iri_ref(&1.subj))}))"

          pattern = "GRAPH #{Virtuoso.iri_ref(graph)} { ?subj #{@has_keywords} ?keywords . }"

          Virtuoso.update(repo, """
          DELETE { #{pattern} }
          WHERE { #{pattern} #{subj_filter} }
          """)

          remaining_query = """
          SELECT ?subj ?keywords WHERE {
            #{pattern}
            #{subj_filter}
          }
          """

          remaining_subjects =
            for row <- Virtuoso.select(repo, remaining_query),
                subj = Virtuoso.iri(row, "subj"),
                do: subj

          remaining = length(remaining_subjects)
          expected = Enum.sum(for group <- in_graph, do: group.source_count)

          if remaining > 0 do
            Logger.warning(
              "#{remaining} of #{expected} hasKeywords statements in graph <#{graph}> survived " <>
                "their deletion; re-running will retry them"
            )
          end

          {deleted + expected - remaining, failures ++ remaining_subjects}
      end

    Logger.debug("Deleted #{deleted} hasKeywords triples")
    {deleted, Enum.uniq(failures)}
  end

  defp parse_comma_sep_list(value) do
    for kw <- String.split(value, ","), kw = String.trim(kw), kw != "", do: kw
  end
end
