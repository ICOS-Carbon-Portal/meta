defmodule BiblioMaterializer.Writer do
  @moduledoc "Builds the materialized triples and writes them with SPARQL INSERT DATA."

  alias BiblioMaterializer.{Sparql, Vocab}

  # One INSERT DATA per subject made every subject its own Virtuoso
  # transaction, which dominated the write path. Batching amortizes the commit
  # over many subjects; the byte cap keeps a request body bounded, which a
  # subject count alone would not — hasBiblioInfo is a JSON document of a few
  # KB.
  @batch_subjects 500
  @batch_bytes 1_000_000

  @doc """
  Groups a stream of per-subject statement lists (see `statement/1`) into
  batches, each of which is one `write_statements/2` call: at most
  #{@batch_subjects} subjects, and cut short of that once the batch's
  statements reach #{div(@batch_bytes, 1000)} kB. A batch keeps its subjects
  separate so the caller can still count them; `Enum.concat/1` gives the
  statements to write.

  Subjects that produce no statements (already materialized, or skipped) still
  take up a slot, so batch boundaries stay independent of how much each
  subject happens to yield.
  """
  def batches(stream) do
    Stream.chunk_while(stream, {[], 0, 0}, &add_to_batch/2, &flush_batch/1)
  end

  defp add_to_batch(statements, {batch, subjects, bytes}) do
    batch = [statements | batch]
    subjects = subjects + 1
    bytes = Enum.reduce(statements, bytes, &(byte_size(&1) + &2))

    if subjects >= @batch_subjects or bytes >= @batch_bytes,
      do: {:cont, Enum.reverse(batch), {[], 0, 0}},
      else: {:cont, {batch, subjects, bytes}}
  end

  defp flush_batch({[], _subjects, _bytes}), do: {:cont, {[], 0, 0}}
  defp flush_batch({batch, _subjects, _bytes}), do: {:cont, Enum.reverse(batch), {[], 0, 0}}

  @doc "Inserts the triples into the graph; returns how many were written."
  def write(graph, triples), do: write_statements(graph, Enum.map(triples, &statement/1))

  @doc """
  Inserts already-serialized statements (see `statement/1`) as a single
  INSERT DATA; returns how many were written. Lets a caller that batches
  many subjects into one update serialize each subject's triples in the
  worker that computed them, and size the batch by the resulting bytes.
  """
  def write_statements(_graph, []), do: 0

  def write_statements(graph, statements) do
    Sparql.update(insert_data(graph, statements))
    length(statements)
  end

  @doc "One triple as a line of an INSERT DATA body."
  def statement({s, p, o}), do: "  <#{s}> <#{p}> #{o} ."

  def all_triples(uri, refs) do
    biblio_triple(uri, refs) ++ citation_triple(uri, refs) ++ licence_triple(uri, refs)
  end

  def biblio_triple(uri, refs), do: [{uri, Vocab.has_biblio_info(), literal(JSON.encode!(refs))}]

  def citation_triple(uri, refs) do
    case refs["citationString"] do
      cit when is_binary(cit) -> [{uri, Vocab.has_citation_string(), literal(cit)}]
      _ -> []
    end
  end

  def licence_triple(uri, refs) do
    case refs["licence"] do
      %{"url" => url} when is_binary(url) -> [{uri, Vocab.dcterms_license(), "<#{url}>"}]
      _ -> []
    end
  end

  defp insert_data(graph, statements) do
    "INSERT DATA { GRAPH <#{graph}> {\n#{Enum.join(statements, "\n")}\n} }"
  end

  defp literal(string) do
    escaped =
      string
      |> String.replace("\\", "\\\\")
      |> String.replace("\"", "\\\"")
      |> String.replace("\n", "\\n")
      |> String.replace("\r", "\\r")
      |> String.replace("\t", "\\t")

    "\"#{escaped}\""
  end
end
