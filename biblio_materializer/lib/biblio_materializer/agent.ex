defmodule BiblioMaterializer.Agent do
  @moduledoc """
  Reads cpmeta agents from the triplestore and renders them as meta's Agent
  JSON: a Person object if the agent has a cpmeta:hasFirstName triple, an
  Organization otherwise (there is no type discriminator in the JSON; meta
  discriminates on the presence of "firstName" when reading).
  """

  alias BiblioMaterializer.{Cache, Rdf, Vocab}
  import BiblioMaterializer.Util, only: [put_opt: 3, last_segment: 1]

  # People and organizations are referenced by many objects; render each
  # agent once per run.
  def read(cache, uri), do: Cache.fetch(cache, {:agent, uri}, fn -> read_uncached(cache, uri) end)

  defp read_uncached(cache, uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        OPTIONAL { <#{uri}> cpmeta:hasFirstName ?firstName }
        OPTIONAL { <#{uri}> cpmeta:hasLastName ?lastName }
        OPTIONAL { <#{uri}> cpmeta:hasEmail ?email }
        OPTIONAL { <#{uri}> cpmeta:hasOrcidId ?orcid }
        OPTIONAL { <#{uri}> cpmeta:hasName ?name }
        OPTIONAL { <#{uri}> rdfs:label ?label }
        OPTIONAL { <#{uri}> rdfs:seeAlso ?website }
      } LIMIT 1
      """)

    if Rdf.val(row, "firstName") do
      person_json(
        cache,
        uri,
        Rdf.val(row, "label"),
        Rdf.val(row, "firstName"),
        Rdf.val(row, "lastName") || "",
        Rdf.val(row, "email"),
        Rdf.val(row, "orcid")
      )
    else
      %{
        "self" => self_resource(cache, uri, Rdf.val(row, "label")),
        "name" => Rdf.val(row, "name") || Rdf.val(row, "label") || last_segment(uri)
      }
      |> put_opt("email", Rdf.val(row, "email"))
      |> put_opt("website", Rdf.val(row, "website"))
      |> put_opt("webpageDetails", webpage_details(cache, uri))
    end
  end

  def person_json(cache, uri, label, first_name, last_name, email, orcid) do
    %{
      "self" => self_resource(cache, uri, label),
      "firstName" => first_name,
      "lastName" => last_name
    }
    |> put_opt("email", email)
    |> put_opt("orcid", normalize_orcid(orcid))
  end

  def self_resource(cache, uri, label) do
    put_opt(%{"uri" => uri, "comments" => comments(cache, uri)}, "label", label)
  end

  # The station-attribution author path (Attribution.authors -> person_json)
  # renders each person directly, bypassing the cached read/1 above, so its
  # comment lookup would otherwise fire once per author per object. An agent's
  # rdfs:comments are shared reference data, so read them once per URI per run.
  defp comments(cache, uri) do
    Cache.fetch(cache, {:comments, uri}, fn ->
      Rdf.values("SELECT ?c WHERE { <#{uri}> rdfs:comment ?c }", "c")
    end)
  end

  defp webpage_details(cache, uri) do
    row =
      Rdf.select_one("""
      SELECT * WHERE {
        <#{uri}> cpmeta:hasWebpageElements ?el .
        OPTIONAL { ?el rdfs:label ?label }
        OPTIONAL { ?el cpmeta:hasCoverImage ?cover }
      } LIMIT 1
      """)

    case Rdf.val(row, "el") do
      nil ->
        nil

      el ->
        link_boxes =
          Rdf.select("""
          SELECT * WHERE {
            <#{el}> cpmeta:hasLinkbox ?lb .
            ?lb cpmeta:hasName ?name ; cpmeta:hasCoverImage ?cover ; cpmeta:hasWebpageLink ?target .
            OPTIONAL { ?lb cpmeta:hasOrderWeight ?weight }
          }
          """)
          |> Enum.map(fn r ->
            %{
              "name" => Rdf.val(r, "name"),
              "coverImage" => Rdf.val(r, "cover"),
              "target" => Rdf.val(r, "target")
            }
            |> put_opt("orderWeight", Rdf.parse_int(Rdf.val(r, "weight")))
          end)
          |> Enum.sort_by(fn lb -> {lb["orderWeight"] == nil, lb["orderWeight"]} end)

        %{"self" => self_resource(cache, el, Rdf.val(row, "label"))}
        |> put_opt("coverImage", Rdf.val(row, "cover"))
        |> put_opt("linkBoxes", if(link_boxes == [], do: nil, else: link_boxes))
    end
  end

  @doc """
  Reads a list of contributor/creator URIs as agents: a single value typed
  rdf:Seq is read as an ordered sequence (rdf:_1, rdf:_2, ...); otherwise
  every value is an agent, sorted Persons-first by (LASTNAME, firstName),
  Organizations by name — meta's agentOrdering.
  """
  def read_contributors(cache, uris) do
    case uris do
      [single] ->
        types = Rdf.values("SELECT ?t WHERE { <#{single}> a ?t }", "t")

        if Vocab.rdf_seq() in types do
          Rdf.select("""
          SELECT ?p ?m WHERE {
            <#{single}> ?p ?m .
            FILTER(STRSTARTS(STR(?p), "#{Vocab.rdf_member_prefix()}"))
          }
          """)
          |> Enum.flat_map(fn r ->
            case Rdf.parse_int(
                   String.replace_prefix(Rdf.val(r, "p"), Vocab.rdf_member_prefix(), "")
                 ) do
              nil -> []
              idx -> [{idx, Rdf.val(r, "m")}]
            end
          end)
          |> Enum.sort()
          |> Enum.map(fn {_idx, member} -> read(cache, member) end)
        else
          read_sorted(cache, uris)
        end

      _ ->
        read_sorted(cache, uris)
    end
  end

  defp read_sorted(cache, uris),
    do: uris |> Enum.map(&read(cache, &1)) |> Enum.sort_by(&sort_key/1)

  @doc "Sort key implementing meta's agentOrdering: Persons before Organizations."
  def sort_key(agent) do
    if person?(agent),
      do: {0, String.upcase(agent["lastName"]), agent["firstName"]},
      else: {1, agent["name"], ""}
  end

  def person?(agent), do: Map.has_key?(agent, "firstName")

  def uri(agent), do: agent["self"]["uri"]

  @doc ~S(Citation-style short form: "Last, F." for persons, the name for organizations.)
  def format_short(agent) do
    if person?(agent),
      do: "#{agent["lastName"]}, #{String.first(agent["firstName"]) || ""}.",
      else: agent["name"]
  end

  @doc ~S(BibTeX/RIS form: "Last, First" for persons, the name for organizations.)
  def format_full(agent) do
    if person?(agent),
      do: "#{agent["lastName"]}, #{agent["firstName"]}",
      else: agent["name"]
  end

  @doc """
  Normalizes an ORCID to the short dashed form meta serializes ("0000-0002-3413-3225"),
  validating the ISO 7064 11-2 check character; invalid values are dropped (nil).
  """
  def normalize_orcid(nil), do: nil

  def normalize_orcid(s) do
    cleaned = s |> String.upcase() |> String.replace(~r/[^0-9X]/, "")

    with 16 <- byte_size(cleaned),
         <<digits::binary-size(15), check::binary-size(1)>> = cleaned,
         true <- Regex.match?(~r/^\d{15}$/, digits),
         true <- check == check_char(digits) do
      cleaned
      |> String.codepoints()
      |> Enum.chunk_every(4)
      |> Enum.map_join("-", &Enum.join/1)
    else
      _ -> nil
    end
  end

  defp check_char(digits) do
    total =
      digits
      |> String.to_charlist()
      |> Enum.reduce(0, fn c, acc -> (acc + (c - ?0)) * 2 end)

    case rem(12 - rem(total, 11), 11) do
      10 -> "X"
      n -> Integer.to_string(n)
    end
  end
end
