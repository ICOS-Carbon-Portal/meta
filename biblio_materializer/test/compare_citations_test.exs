defmodule Mix.Tasks.CompareCitationsTest do
  use ExUnit.Case, async: true

  alias BiblioMaterializer.Vocab

  test "compares only hasBiblioInfo and treats its values as JSON" do
    local = %{
      Vocab.has_citation_string() => MapSet.new(["local citation"]),
      Vocab.dcterms_license() => MapSet.new(["local license"]),
      Vocab.has_biblio_info() => MapSet.new([~s({"title":"Example","authors":["A","B"]})])
    }

    remote = %{
      Vocab.has_citation_string() => MapSet.new(["remote citation"]),
      Vocab.dcterms_license() => MapSet.new(["remote license"]),
      Vocab.has_biblio_info() =>
        MapSet.new([
          """
          {
            "authors": ["A", "B"],
            "title": "Example"
          }
          """
        ])
    }

    assert Mix.Tasks.CompareCitations.equivalent?(local, remote)
  end

  test "detects differences in parsed hasBiblioInfo JSON" do
    local = %{
      Vocab.has_biblio_info() => MapSet.new([~s({"authors":["A","B"]})])
    }

    remote = %{
      Vocab.has_biblio_info() => MapSet.new([~s({"authors":["B","A"]})])
    }

    refute Mix.Tasks.CompareCitations.equivalent?(local, remote)
  end

  test "mismatch output only includes differing JSON keys and their values" do
    predicate = Vocab.has_biblio_info()

    local = %{
      predicate =>
        MapSet.new([
          ~s({"authors":["A","B"],"citationString":"same","localOnly":true})
        ])
    }

    remote = %{
      predicate =>
        MapSet.new([
          ~s({"authors":["B","A"],"citationString":"same","remoteOnly":42})
        ])
    }

    output =
      {"https://example.test/object", local, remote}
      |> Mix.Tasks.CompareCitations.mismatch_text(:log)
      |> IO.iodata_to_binary()

    assert output =~ "    --- local\n"
    assert output =~ "    +++ remote\n"
    assert output =~ ~s(   "authors": [)
    assert output =~ ~s(-    "A")
    assert output =~ ~s(+    "A")
    assert output =~ ~s(-  "localOnly": true)
    assert output =~ ~s(+  "remoteOnly": 42)
    assert output =~ ~r/^    \? +\^/m

    refute output =~ "citationString"
    refute output =~ "\e["

    console_output =
      {"https://example.test/object", local, remote}
      |> Mix.Tasks.CompareCitations.mismatch_text(:console)
      |> IO.iodata_to_binary()

    assert console_output =~ IO.ANSI.red()
    assert console_output =~ IO.ANSI.green()
    assert console_output =~ IO.ANSI.yellow()
    assert console_output =~ IO.ANSI.cyan()
    assert console_output =~ IO.ANSI.underline()
  end

  test "rejects non-positive batch sizes before starting the comparison" do
    assert_raise Mix.Error, ~r/--batch-size must be a positive integer/, fn ->
      Mix.Tasks.CompareCitations.run([
        "--endpoint",
        "https://example.test/sparql",
        "--batch-size",
        "0"
      ])
    end

    assert_raise Mix.Error, ~r/--local-batch-size must be a positive integer/, fn ->
      Mix.Tasks.CompareCitations.run([
        "--endpoint",
        "https://example.test/sparql",
        "--local-batch-size",
        "-1"
      ])
    end
  end

  test "rejects non-positive concurrency before starting the comparison" do
    assert_raise Mix.Error, ~r/--concurrency must be a positive integer/, fn ->
      Mix.Tasks.CompareCitations.run([
        "--endpoint",
        "https://example.test/sparql",
        "--concurrency",
        "0"
      ])
    end
  end
end
