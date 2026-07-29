defmodule Mix.Tasks.CompareCitationsTest do
  use ExUnit.Case, async: true

  alias BiblioMaterializer.Vocab

  test "compares hasBiblioInfo values as JSON" do
    local = %{
      Vocab.has_citation_string() => MapSet.new(["citation"]),
      Vocab.has_biblio_info() => MapSet.new([~s({"title":"Example","authors":["A","B"]})])
    }

    remote = %{
      Vocab.has_citation_string() => MapSet.new(["citation"]),
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
