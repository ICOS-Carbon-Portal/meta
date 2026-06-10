defmodule CitationPopulator.MetaService do
  @moduledoc """
  Client for the citations service HTTP API, which freshly computes the
  citation metadata (the `References` structure) of a static object or
  collection from the triplestore and DataCite.
  """

  alias CitationPopulator.Http

  @collection_class "http://meta.icos-cp.eu/ontologies/cpmeta/Collection"

  @doc """
  Fetches the `references` of the given subject. Returns `{:ok, references_map}`,
  `:not_found`, or `{:error, message}`.
  """
  def fetch_references(subject_uri, class) do
    path = if class == @collection_class, do: "staticcollection", else: "staticobject"

    url =
      Application.fetch_env!(:citation_populator, :citations_service_url) <>
        "/citations/#{path}?" <> URI.encode_query(%{"uri" => subject_uri})

    case Http.get(url, [{"accept", "application/json"}]) do
      {:ok, 200, _headers, body} ->
        case JSON.decode!(body)["references"] do
          refs when is_map(refs) -> {:ok, refs}
          _ -> {:error, "no references in the citations service response"}
        end

      {:ok, 404, _headers, _body} ->
        :not_found

      {:ok, status, _headers, body} ->
        {:error, "citations service responded with HTTP #{status}: #{String.slice(body, 0, 200)}"}

      {:error, reason} ->
        {:error, "citations service request failed: #{inspect(reason)}"}
    end
  end
end
