defmodule LandingBench.Sparql do
  @moduledoc """
  Reproduces the SPARQL query that the data portal issues for its default
  search result list (`listFilteredDataObjects` in the portal's
  `sparqlQueries.ts`): no filters, deprecated objects hidden, sorted by
  submission time descending, paged with offset/limit.
  """

  # Mirrors `metaResourceGraph` + `additionalStationsGraphs` in the portal's config.ts
  @envri_graphs %{
    "ICOS" => [
      "http://meta.icos-cp.eu/resources/cpmeta/",
      "http://meta.icos-cp.eu/resources/icos/",
      "http://meta.icos-cp.eu/resources/extrastations/"
    ],
    "SITES" => ["https://meta.fieldsites.se/resources/sites/"],
    "ICOSCities" => [
      "https://citymeta.icos-cp.eu/resources/cpmeta/",
      "https://citymeta.icos-cp.eu/resources/citymeta/"
    ]
  }

  @spec default_list_query(String.t(), non_neg_integer(), pos_integer()) :: String.t()
  def default_list_query(envri, offset, limit) do
    from_clauses =
      @envri_graphs
      |> Map.get(envri, [])
      |> Enum.map_join("\n", &"from <#{&1}>")

    """
    # listFilteredDataObjects
    prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
    prefix prov: <http://www.w3.org/ns/prov#>
    prefix xsd: <http://www.w3.org/2001/XMLSchema#>
    prefix geo: <http://www.opengis.net/ont/geosparql#>
    select ?dobj ?hasNextVersion ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
    #{from_clauses}
    where {
    \t?spec cpmeta:hasDataLevel [] .
    \tFILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
    \t?dobj cpmeta:hasObjectSpec ?spec .
    \tBIND(EXISTS{[] cpmeta:isNextVersionOf ?dobj} AS ?hasNextVersion)
    \t?dobj cpmeta:hasSizeInBytes ?size .
    \t?dobj cpmeta:hasName ?fileName .
    \t?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
    \t?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
    \t?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
    \tFILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
    }
    order by desc(?submTime)
    offset #{offset} limit #{limit}
    """
  end

  @doc "Runs the default list query, returning the data object URIs and file names of one page."
  @spec list_objects(String.t(), String.t(), non_neg_integer(), pos_integer(), keyword()) ::
          {:ok, [%{uri: String.t(), file_name: String.t()}], float()} | {:error, term()}
  def list_objects(endpoint, envri, offset, limit, req_opts) do
    opts =
      Keyword.merge(req_opts,
        body: default_list_query(envri, offset, limit),
        headers: [{"accept", "application/json"}, {"content-type", "text/plain"}]
      )

    with {:ok, %{status: 200, body: body}, ms} <- LandingBench.Http.timed_post(endpoint, opts),
         {:ok, json} <- JSON.decode(body) do
      rows =
        for b <- json["results"]["bindings"] do
          %{uri: b["dobj"]["value"], file_name: b["fileName"]["value"]}
        end

      {:ok, rows, ms}
    else
      {:ok, %{status: status, body: body}, _ms} ->
        {:error,
         "SPARQL query to #{endpoint} returned HTTP #{status}: #{String.slice(body, 0, 500)}"}

      {:error, err} ->
        {:error, err}
    end
  end
end
