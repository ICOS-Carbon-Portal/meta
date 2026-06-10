defmodule CitationPopulator.DataCite do
  @moduledoc """
  DataCite REST client over plain HTTP, mirroring the Scala
  CitationClient/DoiClient: formatted citation strings (HTML/BibTeX/RIS
  styles) and DOI metadata.

  `map_attributes/2` converts the JSON:API `data.attributes` of a DataCite
  response into exactly the JSON shape that the Scala doi-core library's
  `DoiMeta` spray format writes (conflated creator/contributor names with
  `nameType` markers, capitalized `SchemeURI` in funding references,
  always-present null-able geolocation coordinates, etc.), since meta parses
  the materialized `hasBiblioInfo` literal back with that format.
  """

  require Logger

  alias CitationPopulator.{Http, Throttle}
  import CitationPopulator.Util, only: [put_opt: 3]

  @api "https://api.datacite.org"
  @style "elsevier-harvard"
  @retries 5
  @retry_delay_ms 2_000
  # Cooldown after a 429 when DataCite sends no Retry-After header. Their
  # rate-limit window is 5 minutes, so short retries just burn requests.
  @rate_limit_cooldown_ms 30_000

  @doc "Parses a DOI like the Scala Doi.parse: 10.<digits>/<word chars>, suffix uppercased."
  def parse_doi(s) when is_binary(s) do
    case Regex.run(~r{\A(10\.\d+)/([\w.\-]+)\z}, String.trim(s)) do
      [_, prefix, suffix] -> {prefix, String.upcase(suffix)}
      _ -> nil
    end
  end

  def parse_doi(_), do: nil

  def doi_to_string({prefix, suffix}), do: "#{prefix}/#{suffix}"

  @doc "Fetches a formatted citation string (style :html | :bibtex | :ris)."
  def fetch_citation(doi, style) do
    with {:ok, body} <- get_with_retry(citation_url(doi, style), []) do
      case String.trim(body) do
        "" -> {:error, "got empty citation text"}
        cit -> {:ok, cit}
      end
    end
  end

  defp citation_url({prefix, suffix}, style) do
    case style do
      :html -> "#{@api}/dois/text/x-bibliography/#{prefix}/#{suffix}?style=#{@style}"
      :bibtex -> "#{@api}/dois/application/x-bibtex/#{prefix}/#{suffix}"
      :ris -> "#{@api}/dois/application/x-research-info-systems/#{prefix}/#{suffix}"
    end
  end

  @doc "Fetches DOI metadata and returns it as a DoiMeta-shaped JSON map."
  def fetch_doi_meta({prefix, suffix} = doi) do
    url = "#{@api}/dois/#{prefix}/#{suffix}"

    with {:ok, body} <- get_with_retry(url, [{"accept", "application/vnd.api+json"}]) do
      case JSON.decode(body) do
        {:ok, %{"data" => %{"attributes" => attrs}}} when is_map(attrs) ->
          {:ok, map_attributes(attrs, doi)}

        _ ->
          {:error, "unexpected DataCite metadata response shape"}
      end
    end
  end

  defp get_with_retry(url, headers, attempt \\ 1) do
    Throttle.await()

    case Http.get(url, headers) do
      {:ok, status, _headers, body} when status in 200..299 ->
        {:ok, body}

      # Rate limited: pause the global throttle and retry indefinitely —
      # being over the rate limit must never drop a subject.
      {:ok, 429, resp_headers, _body} ->
        cooldown = retry_after_ms(resp_headers)
        Logger.info("DataCite rate limit hit, backing off for #{div(cooldown, 1000)} s")
        Throttle.backoff(cooldown)
        get_with_retry(url, headers, attempt)

      {:ok, status, _headers, _body} when status in [500, 502, 503] and attempt < @retries ->
        Process.sleep(@retry_delay_ms * attempt)
        get_with_retry(url, headers, attempt + 1)

      {:ok, status, _headers, body} ->
        {:error, "DataCite responded with HTTP #{status}: #{String.slice(body, 0, 200)}"}

      {:error, _reason} when attempt < @retries ->
        Process.sleep(@retry_delay_ms * attempt)
        get_with_retry(url, headers, attempt + 1)

      {:error, reason} ->
        {:error, "DataCite request failed: #{inspect(reason)}"}
    end
  end

  defp retry_after_ms(resp_headers) do
    with {_name, value} <- List.keyfind(resp_headers, "retry-after", 0),
         {seconds, _rest} <- Integer.parse(value) do
      seconds * 1000
    else
      _ -> @rate_limit_cooldown_ms
    end
  end

  @doc false
  # Public for tests: DataCite attributes -> DoiMeta JSON shape.
  def map_attributes(attrs, requested_doi) do
    doi = parse_doi(attrs["doi"] || "") || requested_doi

    %{
      "doi" => doi_to_string(doi),
      "state" => attrs["state"] || "findable",
      "creators" => Enum.map(attrs["creators"] || [], &creator/1),
      "subjects" => subjects(attrs["subjects"] || []),
      "contributors" => Enum.map(attrs["contributors"] || [], &contributor/1),
      "dates" => dates(attrs["dates"] || []),
      "formats" => attrs["formats"] || [],
      "descriptions" => descriptions(attrs["descriptions"] || [])
    }
    |> put_opt("event", attrs["event"])
    |> put_opt("titles", titles(attrs["titles"]))
    |> put_opt("publisher", publisher(attrs["publisher"]))
    |> put_opt("publicationYear", attrs["publicationYear"])
    |> put_opt("types", types(attrs["types"]))
    |> put_opt("version", version(attrs["version"]))
    |> put_opt("rightsList", rights_list(attrs["rightsList"]))
    |> put_opt("url", attrs["url"])
    |> put_opt("fundingReferences", funding_references(attrs["fundingReferences"]))
    |> put_opt("geoLocations", geo_locations(attrs["geoLocations"]))
    |> put_opt("relatedIdentifiers", related_identifiers(attrs["relatedIdentifiers"]))
  end

  defp creator(c) do
    name_fields(c)
    |> Map.put("nameIdentifiers", Enum.flat_map(c["nameIdentifiers"] || [], &name_identifier/1))
    |> Map.put("affiliation", Enum.flat_map(c["affiliation"] || [], &affiliation/1))
  end

  defp contributor(c), do: c |> creator() |> put_opt("contributorType", c["contributorType"])

  # The Scala Name format discriminates on a string familyName and then
  # requires givenName too; anything else is written as an organizational name.
  defp name_fields(c) do
    given = c["givenName"]
    family = c["familyName"]

    if is_binary(family) and family != "" and is_binary(given) and given != "" do
      %{"givenName" => given, "familyName" => family, "nameType" => "Personal"}
    else
      %{"name" => c["name"] || family || given || "", "nameType" => "Organizational"}
    end
  end

  # Known schemes are normalized to the canonical schemeUri the Scala
  # NameIdentifierScheme.lookup assigns (FLUXNET has none).
  @known_scheme_uris %{
    "ORCID" => "http://orcid.org/",
    "ISNI" => "http://www.isni.org/",
    "ROR" => "https://ror.org",
    "FLUXNET" => nil
  }

  defp name_identifier(ni) do
    case ni["nameIdentifier"] do
      id when is_binary(id) and id != "" ->
        scheme = ni["nameIdentifierScheme"] || "Other"

        scheme_uri =
          if Map.has_key?(@known_scheme_uris, scheme),
            do: @known_scheme_uris[scheme],
            else: ni["schemeUri"]

        [
          put_opt(
            %{"nameIdentifier" => id, "nameIdentifierScheme" => scheme},
            "schemeUri",
            scheme_uri
          )
        ]

      _ ->
        []
    end
  end

  defp affiliation(a) when is_binary(a) and a != "", do: [%{"name" => a}]
  defp affiliation(%{"name" => n}) when is_binary(n) and n != "", do: [%{"name" => n}]
  defp affiliation(_), do: []

  defp titles(nil), do: nil

  defp titles(list) do
    for t <- list, is_binary(t["title"]) do
      %{"title" => t["title"]}
      |> put_opt("lang", t["lang"])
      |> put_opt("titleType", t["titleType"])
    end
  end

  # Newer DataCite API versions can return the publisher as an object.
  defp publisher(p) when is_binary(p), do: p
  defp publisher(%{"name" => n}), do: n
  defp publisher(_), do: nil

  defp types(%{} = t) do
    %{}
    |> put_opt("resourceType", t["resourceType"])
    |> put_opt("resourceTypeGeneral", t["resourceTypeGeneral"])
  end

  defp types(_), do: nil

  defp subjects(list) do
    for s <- list, is_binary(s["subject"]) do
      %{"subject" => s["subject"]}
      |> put_opt("lang", s["lang"])
      |> put_opt("valueUri", s["valueUri"])
    end
  end

  defp dates(list) do
    for d <- list, is_binary(d["date"]) do
      put_opt(%{"date" => d["date"]}, "dateType", d["dateType"])
    end
  end

  defp descriptions(list) do
    for d <- list, is_binary(d["description"]) do
      %{"description" => d["description"], "descriptionType" => d["descriptionType"] || "Other"}
      |> put_opt("lang", d["lang"])
    end
  end

  # The Scala Version format only accepts "major.minor".
  defp version(v) when is_binary(v) do
    if Regex.match?(~r/^\d+\.\d+$/, v), do: v
  end

  defp version(_), do: nil

  defp rights_list(nil), do: nil

  defp rights_list(list) do
    for r <- list, is_binary(r["rights"]) do
      %{"rights" => r["rights"]}
      |> put_opt("rightsUri", r["rightsUri"])
      |> put_opt("rightsIdentifier", r["rightsIdentifier"])
      |> put_opt("schemeUri", r["schemeUri"])
      |> put_opt("rightsIdentifierScheme", r["rightsIdentifierScheme"])
      |> put_opt("lang", r["lang"])
    end
  end

  defp funding_references(nil), do: nil

  defp funding_references(list) do
    for f <- list do
      %{}
      |> put_opt("funderName", f["funderName"])
      |> put_opt("funderIdentifier", f["funderIdentifier"])
      |> put_opt("funderIdentifierType", f["funderIdentifierType"])
      # doi-core spells this field with a capitalized prefix
      |> put_opt("SchemeURI", f["SchemeURI"] || f["schemeUri"])
      |> put_opt("awardNumber", f["awardNumber"])
      |> put_opt("awardTitle", f["awardTitle"])
      |> put_opt("awardUri", f["awardUri"])
    end
  end

  defp geo_locations(nil), do: nil

  defp geo_locations(list) do
    for gl <- list, is_map(gl) do
      %{}
      |> put_opt("geoLocationPoint", point(gl["geoLocationPoint"]))
      |> put_opt("geoLocationBox", box(gl["geoLocationBox"]))
      |> put_opt("geoLocationPlace", gl["geoLocationPlace"])
    end
  end

  # Unlike everything else, the lat/lon keys are always written, null when
  # missing (the doi-core formats are custom Option formats).
  defp point(%{} = p) do
    %{"pointLongitude" => num(p["pointLongitude"]), "pointLatitude" => num(p["pointLatitude"])}
  end

  defp point(_), do: nil

  defp box(%{} = b) do
    %{
      "westBoundLongitude" => num(b["westBoundLongitude"]),
      "eastBoundLongitude" => num(b["eastBoundLongitude"]),
      "southBoundLatitude" => num(b["southBoundLatitude"]),
      "northBoundLatitude" => num(b["northBoundLatitude"])
    }
  end

  defp box(_), do: nil

  defp num(n) when is_number(n), do: n

  defp num(s) when is_binary(s) do
    case Float.parse(s) do
      {f, _rest} -> f
      :error -> nil
    end
  end

  defp num(_), do: nil

  defp related_identifiers(nil), do: nil

  defp related_identifiers(list) do
    for r <- list, is_binary(r["relatedIdentifier"]) do
      %{"relatedIdentifier" => r["relatedIdentifier"]}
      |> put_opt("relationType", r["relationType"])
      |> put_opt("relatedIdentifierType", r["relatedIdentifierType"])
      |> put_opt("resourceTypeGeneral", r["resourceTypeGeneral"])
      |> put_opt("relatedMetadataScheme", r["relatedMetadataScheme"])
      |> put_opt("schemeUri", r["schemeUri"])
      |> put_opt("schemeType", r["schemeType"])
    end
  end
end
