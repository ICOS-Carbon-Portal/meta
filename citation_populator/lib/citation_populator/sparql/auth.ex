defmodule CitationPopulator.Sparql.Auth do
  @moduledoc """
  Remembers Virtuoso's authentication challenge for the update endpoint.

  Virtuoso only offers its `WWW-Authenticate` challenge to requests that
  carry no `Authorization` header, so the first update of a run has to be
  sent unauthenticated and rejected to learn how to authenticate at all.
  That answer is then reused: every later update goes out already
  authenticated, halving the request count of the write path — `INSERT DATA`
  is the most frequent request a population pass makes, and re-running the
  rejected probe for each one doubled it.

  Reuse follows RFC 2617: the stored Digest nonce is replayed with a fresh
  cnonce and an incrementing nonce count. A server that refuses replayed
  nonces would answer 401 to every pre-authenticated update, which would be
  slower than never caching at all; when that shows up (several rejections
  in quick succession) reuse is switched off and updates go back to the
  probe-then-authenticate exchange.

  The stored challenge is versioned by a generation counter so that
  concurrent writers racing on the same stale challenge only replace it
  once. A refresh always answers with a header derived from the challenge
  the caller just received, so the retry is valid whichever writer won.
  """

  use GenServer
  require Logger

  # Nonces do expire legitimately, minutes apart; a server that refuses
  # replay rejects every single update, milliseconds apart. Only rejections
  # that cluster inside the window count towards giving up.
  @max_rejects 3
  @reject_window_ms 5_000

  def start_link(opts) do
    GenServer.start_link(__MODULE__, nil, name: Keyword.get(opts, :name, __MODULE__))
  end

  @doc """
  The `Authorization` header value to send with an update, or nil when
  nothing is cached yet (the request is then the probe that fetches the
  challenge). The generation identifies the challenge it came from and must
  be handed back to `refresh/4`.
  """
  def authorization(uri_path, server \\ __MODULE__),
    do: GenServer.call(server, {:authorization, uri_path})

  @doc """
  Parses a `www-authenticate` header list into a challenge term. Raises when
  the server offers nothing we can answer.
  """
  def challenge!(["Digest " <> params | _]), do: {:digest, parse_challenge(params)}
  def challenge!(["Basic" <> _ | _]), do: :basic

  def challenge!(other) do
    raise "SPARQL update was refused (HTTP 401) " <>
            "and no supported auth challenge was offered: #{inspect(other)}"
  end

  @doc """
  Answers a freshly received challenge, caching it for later updates when
  `gen` is still the current generation. `cached?` says whether the rejected
  request already carried a cached header — i.e. whether this is a genuine
  rejection rather than the expected first probe.
  """
  def refresh(gen, challenge, cached?, uri_path, server \\ __MODULE__) do
    GenServer.call(server, {:refresh, gen, challenge, cached?, uri_path})
  end

  @impl true
  def init(nil) do
    {:ok, %{gen: 0, challenge: nil, nc: 0, reuse?: true, rejects: 0, rejected_at: nil}}
  end

  @impl true
  def handle_call({:authorization, uri_path}, _from, state) do
    if state.challenge == nil or not state.reuse? do
      {:reply, {state.gen, nil}, state}
    else
      state = %{state | nc: state.nc + 1}
      {:reply, {state.gen, header(state.challenge, state.nc, uri_path)}, state}
    end
  end

  def handle_call({:refresh, gen, challenge, cached?, uri_path}, _from, state) do
    state = if gen == state.gen, do: adopt(state, challenge, cached?), else: state

    # Built from the challenge the caller just received rather than from the
    # stored one: under a race the stored challenge may be a different (also
    # fresh) one, and a nonce may only be counted from by whoever holds it.
    {:reply, header(challenge, 1, uri_path), state}
  end

  # The refresher's own retry consumes nonce count 1, so the next handout
  # starts from 2.
  defp adopt(state, challenge, cached?) do
    state = if cached?, do: count_rejection(state), else: state
    %{state | challenge: challenge, gen: state.gen + 1, nc: 1}
  end

  defp count_rejection(state) do
    now = System.monotonic_time(:millisecond)

    rejects =
      if state.rejected_at && now - state.rejected_at <= @reject_window_ms,
        do: state.rejects + 1,
        else: 1

    state = %{state | rejects: rejects, rejected_at: now}

    if rejects > @max_rejects do
      Logger.warning(
        "Virtuoso rejected #{rejects} pre-authenticated updates in a row; it does not " <>
          "accept replayed auth challenges — falling back to probing before every update"
      )

      %{state | reuse?: false}
    else
      state
    end
  end

  defp header(:basic, _nc, _uri_path),
    do: "Basic " <> Base.encode64("#{username()}:#{password()}")

  defp header({:digest, challenge}, nc, uri_path),
    do: digest_authorization(challenge, "POST", uri_path, nc)

  defp parse_challenge(params) do
    ~r/(\w+)=(?:"([^"]*)"|([^,\s]+))/
    |> Regex.scan(params)
    |> Map.new(fn [_full, key | values] ->
      {key, values |> Enum.reject(&(&1 == "")) |> List.first("")}
    end)
  end

  defp digest_authorization(challenge, method, uri_path, nc_int) do
    user = username()
    realm = Map.fetch!(challenge, "realm")
    nonce = Map.fetch!(challenge, "nonce")

    ha1 = md5_hex("#{user}:#{realm}:#{password()}")
    ha2 = md5_hex("#{method}:#{uri_path}")

    {response, qop_fields} =
      if challenge["qop"] && "auth" in String.split(challenge["qop"], ",") do
        cnonce = Base.encode16(:crypto.strong_rand_bytes(8), case: :lower)
        nc = nc_int |> Integer.to_string(16) |> String.downcase() |> String.pad_leading(8, "0")

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

  defp username, do: Application.fetch_env!(:citation_populator, :virtuoso_username)
  defp password, do: Application.fetch_env!(:citation_populator, :virtuoso_password)
end
