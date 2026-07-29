defmodule CitationPopulator.SparqlAuthTest do
  use ExUnit.Case, async: true

  alias CitationPopulator.Sparql.Auth

  @path "/sparql-auth"
  @digest ~s(Digest realm="SPARQL", qop="auth", nonce="abc123", opaque="op")

  setup context do
    # The application starts a globally named Auth; these tests need their own
    # so that switching challenge reuse off does not leak between them.
    server = :"auth_#{context.test |> to_string() |> String.replace(" ", "_")}"
    start_supervised!({Auth, name: server})
    %{server: server}
  end

  describe "challenge!/1" do
    test "parses a Digest challenge's quoted and bare parameters" do
      assert {:digest, challenge} = Auth.challenge!([@digest])

      assert challenge["realm"] == "SPARQL"
      assert challenge["qop"] == "auth"
      assert challenge["nonce"] == "abc123"
      assert challenge["opaque"] == "op"
    end

    test "recognizes a Basic challenge" do
      assert Auth.challenge!([~s(Basic realm="SPARQL")]) == :basic
    end

    test "raises when the server offers nothing we can answer" do
      assert_raise RuntimeError, ~r/no supported auth challenge/, fn ->
        Auth.challenge!(["Negotiate"])
      end
    end
  end

  test "the first update is unauthenticated, so it can fetch the challenge", %{server: server} do
    assert {_gen, nil} = Auth.authorization(@path, server)
  end

  test "a remembered Digest challenge is replayed with an incrementing nonce count", %{
    server: server
  } do
    {gen, nil} = Auth.authorization(@path, server)
    challenge = Auth.challenge!([@digest])

    # The refreshing writer's own retry consumes nonce count 1.
    assert Auth.refresh(gen, challenge, false, @path, server) =~ ~s(nc=00000001)

    {_gen, second} = Auth.authorization(@path, server)
    {_gen, third} = Auth.authorization(@path, server)

    assert second =~ ~s(nonce="abc123")
    assert second =~ ~s(uri="#{@path}")
    assert second =~ ~s(nc=00000002)
    assert third =~ ~s(nc=00000003)

    # A fresh cnonce per request, hence a different response digest.
    refute second == third
  end

  test "a remembered Basic challenge is replayed verbatim", %{server: server} do
    {gen, nil} = Auth.authorization(@path, server)
    Auth.refresh(gen, Auth.challenge!([~s(Basic realm="SPARQL")]), false, @path, server)

    {_gen, header} = Auth.authorization(@path, server)
    assert header == "Basic " <> Base.encode64("dba:dba")
  end

  test "only the first of several writers racing on a stale challenge replaces it", %{
    server: server
  } do
    challenge = Auth.challenge!([@digest])
    {gen0, nil} = Auth.authorization(@path, server)

    Auth.refresh(gen0, challenge, false, @path, server)
    {gen1, _header} = Auth.authorization(@path, server)
    assert gen1 == gen0 + 1

    # A writer that still holds the superseded generation gets a usable header
    # of its own without replacing the stored challenge a second time.
    assert Auth.refresh(gen0, challenge, true, @path, server) =~ ~s(nc=00000001)
    assert {^gen1, _header} = Auth.authorization(@path, server)
  end

  test "a server that refuses replayed challenges turns reuse off", %{server: server} do
    challenge = Auth.challenge!([@digest])
    {gen, nil} = Auth.authorization(@path, server)
    Auth.refresh(gen, challenge, false, @path, server)

    # Every pre-authenticated update now comes back 401: refresh, retry,
    # rejected again. After a few rounds reuse is abandoned and updates go
    # back to probing, i.e. authorization/2 answering nil.
    Enum.reduce_while(1..10, nil, fn _i, _acc ->
      case Auth.authorization(@path, server) do
        {_gen, nil} -> {:halt, :probing}
        {gen, _header} -> {:cont, Auth.refresh(gen, challenge, true, @path, server)}
      end
    end)

    assert {_gen, nil} = Auth.authorization(@path, server)
  end
end
