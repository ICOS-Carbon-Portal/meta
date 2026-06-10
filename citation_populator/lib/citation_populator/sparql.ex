defmodule CitationPopulator.Sparql do
  @moduledoc """
  Minimal SPARQL 1.1 protocol client: raw queries and updates over HTTP,
  no triplestore driver.

  Queries go unauthenticated to `<virtuoso>/sparql`. Updates go to
  `<virtuoso>/sparql-auth`: the first attempt carries no credentials —
  Virtuoso only offers its WWW-Authenticate challenge to requests without
  an Authorization header — and the challenge is then answered with Digest
  or Basic auth, whichever the server asks for.
  """

  # Transient failures are retried with exponential backoff: transport
  # errors (Virtuoso closes idle keep-alive connections, and a request
  # racing such a close surfaces as "socket closed") and 5xx responses
  # (Virtuoso answers 503 when overloaded). That's safe for queries, and
  # for our updates too — INSERT DATA is idempotent. Anything else fails
  # immediately: a genuinely rejected update must fail the run. Bodies are
  # decoded by us (SPARQL JSON results have their own media type), and the
  # big paged queries need a generous timeout.
  @req_options [
    retry: &__MODULE__.transient?/2,
    max_retries: 5,
    retry_log_level: :info,
    decode_body: false,
    receive_timeout: 600_000
  ]

  @doc false
  def transient?(_req, %Req.Response{status: status}), do: status in [500, 502, 503]
  def transient?(_req, _exception), do: true

  @page_size 100_000

  @doc """
  Lazily streams the binding maps of a SELECT query in pages, using
  Virtuoso's "scrollable cursor" pattern — ORDER BY in a subselect with
  OFFSET/LIMIT outside (a plain sorted LIMIT/OFFSET is rejected beyond
  MaxSortedTopRows, and unpaged results are silently truncated at
  ResultSetMaxRows). The offset advances by the rows actually received and
  the stream only ends on an empty page, so it stays correct under any
  per-result row cap. The query must not itself use ORDER BY/LIMIT/OFFSET.
  """
  def select_stream(query, order_by) do
    Stream.resource(
      fn -> 0 end,
      fn offset ->
        page =
          select(
            "SELECT * WHERE { { #{query} ORDER BY #{order_by} } } " <>
              "LIMIT #{@page_size} OFFSET #{offset}"
          )

        case page do
          [] -> {:halt, offset}
          rows -> {rows, offset + length(rows)}
        end
      end,
      fn _offset -> :ok end
    )
  end

  @doc "Runs a SELECT query and returns the list of binding maps from the JSON results."
  def select(query) do
    headers = [{"accept", "application/sparql-results+json"}]

    case post_form(query_endpoint(), %{"query" => query}, headers) do
      {:ok, %Req.Response{status: 200, body: body}} ->
        JSON.decode!(body)["results"]["bindings"]

      other ->
        raise "SPARQL query failed: #{describe(other)}"
    end
  end

  @doc "Runs a SPARQL update against the authenticated endpoint. Raises on failure."
  def update(update) do
    params = %{"update" => update}

    case post_form(update_endpoint(), params) do
      {:ok, %Req.Response{status: status}} when status in 200..299 ->
        :ok

      {:ok, %Req.Response{status: 401} = resp} ->
        update_authenticated(params, authorization(resp))

      other ->
        raise "SPARQL update failed: #{describe(other)}"
    end
  end

  defp update_authenticated(params, auth) do
    case post_form(update_endpoint(), params, [{"authorization", auth}]) do
      {:ok, %Req.Response{status: status}} when status in 200..299 -> :ok
      other -> raise "SPARQL update failed (authenticated): #{describe(other)}"
    end
  end

  defp post_form(url, params, headers \\ []) do
    Req.post(url, [form: params, headers: headers] ++ @req_options)
  end

  defp authorization(resp) do
    case Req.Response.get_header(resp, "www-authenticate") do
      ["Digest " <> params | _] ->
        digest_authorization(parse_challenge(params), "POST", URI.parse(update_endpoint()).path)

      ["Basic" <> _ | _] ->
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

  defp describe({:ok, %Req.Response{status: status, body: body}}),
    do: "HTTP #{status}: #{String.slice(body, 0, 500)}"

  defp describe({:error, exception}), do: Exception.message(exception)

  defp query_endpoint, do: virtuoso_host() <> "/sparql"
  defp update_endpoint, do: virtuoso_host() <> "/sparql-auth"
  defp virtuoso_host, do: Application.fetch_env!(:citation_populator, :virtuoso_host)
  defp username, do: Application.fetch_env!(:citation_populator, :virtuoso_username)
  defp password, do: Application.fetch_env!(:citation_populator, :virtuoso_password)
end
