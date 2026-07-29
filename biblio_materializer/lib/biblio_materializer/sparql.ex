defmodule BiblioMaterializer.Sparql do
  @moduledoc """
  Minimal SPARQL 1.1 protocol client: raw queries and updates over HTTP,
  no triplestore driver.

  Queries go unauthenticated to `<virtuoso>/sparql`. Updates go to
  `<virtuoso>/sparql-auth`, authenticated with Digest or Basic auth,
  whichever the server asks for; the challenge exchange and the reuse of its
  answer across updates live in [`Sparql.Auth`](`BiblioMaterializer.Sparql.Auth`).
  """

  alias BiblioMaterializer.Sparql.Auth

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
  def select_stream(query, order_by, endpoint \\ query_endpoint(), opts \\ []) do
    endpoint = endpoint || query_endpoint()
    page_size = Keyword.get(opts, :page_size, @page_size)
    on_page = Keyword.get(opts, :on_page, fn _offset, _count -> :ok end)

    Stream.resource(
      fn -> 0 end,
      fn offset ->
        page =
          select(
            "SELECT * WHERE { { #{query} ORDER BY #{order_by} } } " <>
              "LIMIT #{page_size} OFFSET #{offset}",
            endpoint
          )

        on_page.(offset, length(page))

        case page do
          [] -> {:halt, offset}
          rows -> {rows, offset + length(rows)}
        end
      end,
      fn _offset -> :ok end
    )
  end

  @doc """
  Runs a SELECT query against `endpoint` (the local Virtuoso by default) and
  returns the list of binding maps from the JSON results.
  """
  def select(query, endpoint \\ query_endpoint()) do
    headers = [{"accept", "application/sparql-results+json"}]

    case post_form(endpoint, %{"query" => query}, headers) do
      {:ok, %Req.Response{status: 200, body: body}} ->
        JSON.decode!(body)["results"]["bindings"]

      other ->
        raise "SPARQL query failed: #{describe(other)}"
    end
  end

  @doc "Runs a SPARQL update against the authenticated endpoint. Raises on failure."
  def update(update) do
    params = %{"update" => update}
    uri_path = URI.parse(update_endpoint()).path
    {gen, auth} = Auth.authorization(uri_path)

    case post_form(update_endpoint(), params, authorization_header(auth)) do
      {:ok, %Req.Response{status: status}} when status in 200..299 ->
        :ok

      # Either the challenge-fetching probe of the run's first update (auth is
      # nil then) or a cached challenge gone stale. Both are answered with the
      # challenge this response carries.
      {:ok, %Req.Response{status: 401} = resp} ->
        challenge = Auth.challenge!(Req.Response.get_header(resp, "www-authenticate"))
        update_authenticated(params, Auth.refresh(gen, challenge, auth != nil, uri_path))

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

  defp authorization_header(nil), do: []
  defp authorization_header(auth), do: [{"authorization", auth}]

  defp post_form(url, params, headers) do
    Req.post(url, [form: params, headers: headers] ++ @req_options)
  end

  defp describe({:ok, %Req.Response{status: status, body: body}}),
    do: "HTTP #{status}: #{String.slice(body, 0, 500)}"

  defp describe({:error, exception}), do: Exception.message(exception)

  defp query_endpoint, do: virtuoso_host() <> "/sparql"
  defp update_endpoint, do: virtuoso_host() <> "/sparql-auth"
  defp virtuoso_host, do: Application.fetch_env!(:biblio_materializer, :virtuoso_host)
end
