defmodule Keywords.Virtuoso do
  @moduledoc """
  The Virtuoso triplestore over its plain and authenticated SPARQL endpoints:
  queries go to `/sparql`, updates to `/sparql-auth`, which answers a first
  unauthenticated request with an HTTP Digest challenge.

  Terms are handed to the callers as SPARQL-results JSON binding maps; the
  helpers below pick the IRI or literal out of one binding.
  """

  defstruct [:host, :username, :password]

  def new(host, username, password) do
    %__MODULE__{host: host, username: username, password: password}
  end

  @doc "Runs a SPARQL query, returning its rows as binding maps."
  def select(repo, query) do
    body = post!(repo, repo.host <> "/sparql", "query=" <> URI.encode_www_form(query), false)
    :json.decode(body)["results"]["bindings"]
  end

  @doc "Runs a SPARQL update against the authenticated endpoint."
  def update(repo, update) do
    post!(repo, repo.host <> "/sparql-auth", "update=" <> URI.encode_www_form(update), true)
    :ok
  end

  @doc "The IRI bound to `name`, or nil if the binding is absent or not an IRI."
  def iri(row, name) do
    case row[name] do
      %{"type" => "uri", "value" => value} -> value
      _ -> nil
    end
  end

  @doc "The lexical form of the literal bound to `name`, or nil if it is not a literal."
  def literal(row, name) do
    case row[name] do
      %{"type" => type, "value" => value} when type in ["literal", "typed-literal"] -> value
      _ -> nil
    end
  end

  @doc """
  A string as a SPARQL plain literal, that is, without the `^^xsd:string` datatype.

  The two are the same term in RDF 1.1, but Virtuoso does not treat them as such:
  a plain literal in the store is matched neither by `"kw"^^xsd:string` in a DELETE
  nor in a query, and vice versa, silently and without error. The escaping covers
  non-printable and non-ASCII characters too, so that not even the control
  characters our data is known to contain can break a query.
  """
  def plain_literal(value), do: ~s(") <> escape(value) <> ~s(")

  @doc "A string as an `xsd:string`-typed literal, as the portal's derived graph holds them."
  def typed_literal(value),
    do: plain_literal(value) <> "^^<http://www.w3.org/2001/XMLSchema#string>"

  @doc "An IRI in the `<...>` form."
  def iri_ref(iri), do: "<" <> escape(iri) <> ">"

  defp escape(value) do
    for char <- String.to_charlist(value), into: "", do: escape_char(char)
  end

  defp escape_char(?\\), do: "\\\\"
  defp escape_char(?"), do: "\\\""
  defp escape_char(?\n), do: "\\n"
  defp escape_char(?\r), do: "\\r"
  defp escape_char(?\t), do: "\\t"

  defp escape_char(char) when char < 0x20 or char > 0x7E,
    do: "\\u" <> (char |> Integer.to_string(16) |> String.pad_leading(4, "0") |> String.upcase())

  defp escape_char(char), do: <<char::utf8>>

  # Sends a form-encoded POST, adding digest authentication when the endpoint
  # challenges for it. Failures raise: the single pass of the run is aborted.
  defp post!(repo, url, body, authenticated?) do
    case request(url, body, []) do
      {:ok, 401, headers, _body} when authenticated? ->
        auth = authorization(repo, headers, url)

        case request(url, body, [{~c"authorization", String.to_charlist(auth)}]) do
          {:ok, status, _headers, response} when status in 200..299 -> response
          other -> raise "SPARQL request to #{url} failed: #{inspect(other)}"
        end

      {:ok, status, _headers, response} when status in 200..299 ->
        response

      other ->
        raise "SPARQL request to #{url} failed: #{inspect(other)}"
    end
  end

  defp request(url, body, headers) do
    request = {
      String.to_charlist(url),
      [{~c"accept", ~c"application/sparql-results+json"} | headers],
      ~c"application/x-www-form-urlencoded",
      body
    }

    case :httpc.request(:post, request, [{:timeout, :infinity}], body_format: :binary) do
      {:ok, {{_version, status, _reason}, response_headers, response_body}} ->
        {:ok, status, response_headers, response_body}

      {:error, reason} ->
        {:error, reason}
    end
  end

  defp authorization(repo, headers, url) do
    challenge = parse_challenge(headers)
    path = URI.parse(url).path
    cnonce = 16 |> :crypto.strong_rand_bytes() |> Base.encode16(case: :lower)
    nc = "00000001"
    ha1 = md5("#{repo.username}:#{challenge["realm"]}:#{repo.password}")
    ha2 = md5("POST:#{path}")

    response =
      case challenge["qop"] do
        nil -> md5("#{ha1}:#{challenge["nonce"]}:#{ha2}")
        _ -> md5("#{ha1}:#{challenge["nonce"]}:#{nc}:#{cnonce}:auth:#{ha2}")
      end

    fields =
      [
        {"username", repo.username},
        {"realm", challenge["realm"]},
        {"nonce", challenge["nonce"]},
        {"uri", path},
        {"response", response}
      ] ++
        if(challenge["opaque"], do: [{"opaque", challenge["opaque"]}], else: []) ++
        if challenge["qop"] do
          [{"qop", "auth"}, {"nc", nc}, {"cnonce", cnonce}]
        else
          []
        end

    "Digest " <> Enum.map_join(fields, ", ", fn {key, value} -> ~s(#{key}="#{value}") end)
  end

  defp parse_challenge(headers) do
    header =
      Enum.find_value(headers, "", fn {name, value} ->
        if to_string(name) |> String.downcase() == "www-authenticate", do: to_string(value)
      end)

    for [_, key, value] <- Regex.scan(~r/(\w+)="?([^",]*)"?/, header),
        into: %{},
        do: {String.downcase(key), value}
  end

  defp md5(string), do: :crypto.hash(:md5, string) |> Base.encode16(case: :lower)
end
