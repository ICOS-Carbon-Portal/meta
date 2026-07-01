defmodule CitationPopulator.Licence do
  @moduledoc """
  Licence resolution chain, port of LiveCitationMaker.getLicence:
  the object's own dcterms:license, else the licence implied by its spec,
  else by the spec's project, else the ENVRI default.
  """

  alias CitationPopulator.{Cache, Envri, Rdf}
  import CitationPopulator.Util, only: [put_opt: 3]

  def resolve(obj_uri, spec_uri, project_uri, envri, derived_graph) do
    lic_uri =
      own_licence_uri(obj_uri, derived_graph) || implied(spec_uri) || implied(project_uri)

    if lic_uri, do: read(lic_uri), else: Envri.default_licence(envri)
  end

  # The populator itself writes dcterms:license triples into the derived
  # graph, so that graph must be excluded or it would read its own output.
  defp own_licence_uri(uri, derived_graph) do
    row =
      Rdf.select_one("""
      SELECT ?lic WHERE {
        GRAPH ?g { <#{uri}> dcterms:license ?lic }
        FILTER(?g != <#{derived_graph}>)
      } LIMIT 1
      """)

    Rdf.val(row, "lic")
  end

  defp implied(nil), do: nil

  # The implied licence of a spec or project is shared reference data.
  defp implied(uri) do
    Cache.fetch({:implied, uri}, fn ->
      row =
        Rdf.select_one("""
        SELECT ?lic WHERE { <#{uri}> cpmeta:impliesDefaultLicence ?lic } LIMIT 1
        """)

      Rdf.val(row, "lic")
    end)
  end

  # There are only a handful of licences; read each one once per run.
  defp read(lic_uri) do
    Cache.fetch({:licence, lic_uri}, fn -> read_uncached(lic_uri) end)
  end

  defp read_uncached(lic_uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        OPTIONAL { <#{lic_uri}> rdfs:label ?name }
        OPTIONAL { <#{lic_uri}> rdfs:seeAlso ?webpage }
        OPTIONAL { <#{lic_uri}> skos:exactMatch ?base }
      } LIMIT 1
      """)

    %{
      "url" => lic_uri,
      # Scala requires the label and fails the whole References computation
      # without it; falling back to the URI is the lenient choice here.
      "name" => Rdf.val(row, "name") || lic_uri,
      "webpage" => Rdf.val(row, "webpage") || lic_uri
    }
    |> put_opt("baseLicence", Rdf.val(row, "base"))
  end
end
