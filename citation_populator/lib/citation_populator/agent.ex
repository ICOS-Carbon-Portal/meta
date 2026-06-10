defmodule CitationPopulator.Agent do
  @moduledoc """
  Reads cpmeta agents from the triplestore and renders them as meta's Agent
  JSON: a Person object if the agent has a cpmeta:hasFirstName triple, an
  Organization otherwise (there is no type discriminator in the JSON; meta
  discriminates on the presence of "firstName" when reading).
  """

  alias CitationPopulator.{Rdf, Vocab}
  import CitationPopulator.Util, only: [put_opt: 3, last_segment: 1]

  def read(uri) do
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
        uri,
        Rdf.val(row, "label"),
        Rdf.val(row, "firstName"),
        Rdf.val(row, "lastName") || "",
        Rdf.val(row, "email"),
        Rdf.val(row, "orcid")
      )
    else
      %{
        "self" => self_resource(uri, Rdf.val(row, "label")),
        "name" => Rdf.val(row, "name") || Rdf.val(row, "label") || last_segment(uri)
      }
      |> put_opt("email", Rdf.val(row, "email"))
      |> put_opt("website", Rdf.val(row, "website"))
      |> put_opt("webpageDetails", webpage_details(uri))
    end
  end

  def person_json(uri, label, first_name, last_name, email, orcid) do
    %{
      "self" => self_resource(uri, label),
      "firstName" => first_name,
      "lastName" => last_name
    }
    |> put_opt("email", email)
    |> put_opt("orcid", normalize_orcid(orcid))
  end

  def self_resource(uri, label) do
    comments = Rdf.values("SELECT ?c WHERE { <#{uri}> rdfs:comment ?c }", "c")
    put_opt(%{"uri" => uri, "comments" => comments}, "label", label)
  end

  defp webpage_details(uri) do
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

        %{"self" => self_resource(el, Rdf.val(row, "label"))}
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
  def read_contributors(uris) do
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
          |> Enum.map(fn {_idx, member} -> read(member) end)
        else
          read_sorted(uris)
        end

      _ ->
        read_sorted(uris)
    end
  end

  defp read_sorted(uris), do: uris |> Enum.map(&read/1) |> Enum.sort_by(&sort_key/1)

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
