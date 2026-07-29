defmodule BiblioMaterializer.Licence do
  @moduledoc """
  Licence resolution chain, port of LiveCitationMaker.getLicence:
  the object's own dcterms:license, else the licence implied by its spec,
  else by the spec's project, else the ENVRI default.

  The object's own licence comes from its prefetched core metadata; the rest
  is shared reference data, memoized for the run.
  """

  alias BiblioMaterializer.{Cache, Envri, Rdf}
  import BiblioMaterializer.Util, only: [put_opt: 3]

  def resolve(cache, own_licence_uri, spec_uri, project_uri, envri) do
    lic_uri =
      own_licence_uri || implied(cache, spec_uri) || implied(cache, project_uri)

    if lic_uri, do: read(cache, lic_uri), else: Envri.default_licence(envri)
  end

  defp implied(_cache, nil), do: nil

  # The implied licence of a spec or project is shared reference data.
  defp implied(cache, uri) do
    Cache.fetch(cache, {:implied, uri}, fn ->
      row =
        Rdf.select_one("""
        SELECT ?lic WHERE { <#{uri}> cpmeta:impliesDefaultLicence ?lic } LIMIT 1
        """)

      Rdf.val(row, "lic")
    end)
  end

  # There are only a handful of licences; read each one once per run.
  defp read(cache, lic_uri) do
    Cache.fetch(cache, {:licence, lic_uri}, fn -> read_uncached(lic_uri) end)
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
