defmodule CitationPopulator.Sparql do
  @moduledoc """
  Minimal SPARQL 1.1 protocol client: raw queries and updates over HTTP,
  no triplestore driver.

  Queries go unauthenticated to `<virtuoso>/sparql`. Updates go to
  `<virtuoso>/sparql-auth` with Basic auth first, falling back to Digest
  auth (Virtuoso's default challenge) when the server demands it.
  """

  alias CitationPopulator.Http

  @doc "Runs a SELECT query and returns the list of binding maps from the JSON results."
  def select(query) do
    headers = [{"accept", "application/sparql-results+json"}]

    case Http.post_form(query_endpoint(), %{"query" => query}, headers) do
      {:ok, 200, _headers, body} -> JSON.decode!(body)["results"]["bindings"]
      other -> raise "SPARQL query failed: #{describe(other)}"
    end
  end

  @doc """
  Runs a SPARQL update against the authenticated endpoint. Raises on failure.

  The first attempt carries no credentials: Virtuoso only offers its
  WWW-Authenticate challenge to requests without an Authorization header
  (a preemptively-authenticated request gets a bare 401). The challenge is
  then answered with Digest or Basic auth, whichever the server asks for.
  """
  def update(update) do
    params = %{"update" => update}

    case Http.post_form(update_endpoint(), params, []) do
      {:ok, status, _headers, _body} when status in 200..299 -> :ok
      {:ok, 401, headers, _body} -> update_authenticated(params, authorization(headers))
      other -> raise "SPARQL update failed: #{describe(other)}"
    end
  end

  defp update_authenticated(params, auth) do
    case Http.post_form(update_endpoint(), params, [{"authorization", auth}]) do
      {:ok, status, _headers, _body} when status in 200..299 -> :ok
      other -> raise "SPARQL update failed (authenticated): #{describe(other)}"
    end
  end

  defp authorization(headers) do
    case List.keyfind(headers, "www-authenticate", 0) do
      {_, "Digest " <> params} ->
        digest_authorization(parse_challenge(params), "POST", URI.parse(update_endpoint()).path)

      {_, "Basic" <> _} ->
        "Basic " <> Base.encode64("#{username()}:#{password()}")

      other ->
        raise "SPARQL update was refused (HTTP 401) " <>
                "and no supported auth challenge was offered: #{inspect(other)}"
    end
  end

  defp parse_challenge(params) do
    ~r/(\w+)=(?:"([^"]*)"|([^,\s]+))/
    |> Regex.scan(params)
    |> Map.new(fn [_full, key | values] ->
      {key, values |> Enum.reject(&(&1 == "")) |> List.first("")}
    end)
  end

  defp digest_authorization(challenge, method, uri_path) do
    user = username()
    realm = Map.fetch!(challenge, "realm")
    nonce = Map.fetch!(challenge, "nonce")

    ha1 = md5_hex("#{user}:#{realm}:#{password()}")
    ha2 = md5_hex("#{method}:#{uri_path}")

    {response, qop_fields} =
      if challenge["qop"] && "auth" in String.split(challenge["qop"], ",") do
        cnonce = Base.encode16(:crypto.strong_rand_bytes(8), case: :lower)
        nc = "00000001"

        {md5_hex("#{ha1}:#{nonce}:#{nc}:#{cnonce}:auth:#{ha2}"),
         ["qop=auth", "nc=#{nc}", ~s(cnonce="#{cnonce}")]}
      else
        {md5_hex("#{ha1}:#{nonce}:#{ha2}"), []}
      end

    opaque_fields =
      case challenge["opaque"] do
        nil -> []
        opaque -> [~s(opaque="#{opaque}")]
      end

    fields =
      [~s(username="#{user}"), ~s(realm="#{realm}"), ~s(nonce="#{nonce}"), ~s(uri="#{uri_path}")] ++
        qop_fields ++ [~s(response="#{response}")] ++ opaque_fields

    "Digest " <> Enum.join(fields, ", ")
  end

  defp md5_hex(string), do: :crypto.hash(:md5, string) |> Base.encode16(case: :lower)

  defp describe({:ok, status, _headers, body}),
    do: "HTTP #{status}: #{String.slice(body, 0, 500)}"

  defp describe({:error, reason}), do: inspect(reason)

  defp query_endpoint, do: virtuoso_host() <> "/sparql"
  defp update_endpoint, do: virtuoso_host() <> "/sparql-auth"
  defp virtuoso_host, do: Application.fetch_env!(:citation_populator, :virtuoso_host)
  defp username, do: Application.fetch_env!(:citation_populator, :virtuoso_username)
  defp password, do: Application.fetch_env!(:citation_populator, :virtuoso_password)
end
