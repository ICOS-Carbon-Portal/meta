defmodule BiblioMaterializer.WriterTest do
  use ExUnit.Case, async: true

  alias BiblioMaterializer.Writer

  @subject "https://meta.icos-cp.eu/objects/abc"

  describe "statement/1" do
    test "serializes a triple as an INSERT DATA line" do
      assert Writer.statement({@subject, "http://p", ~s("x")}) ==
               ~s(  <#{@subject}> <http://p> "x" .)
    end
  end

  describe "batches/1" do
    test "keeps subjects separate and their statements concatenable" do
      batches = batches([[a(1), a(2)], [], [a(3)]])

      assert batches == [[[a(1), a(2)], [], [a(3)]]]
      assert Enum.concat(hd(batches)) == [a(1), a(2), a(3)]
    end

    test "emits the trailing partial batch" do
      subjects = List.duplicate([a(1)], 501)

      assert [full, partial] = batches(subjects)
      assert length(full) == 500
      assert length(partial) == 1
    end

    test "loses nothing across batch boundaries" do
      subjects = Enum.map(1..1499, &[a(&1)])

      assert subjects |> batches() |> Enum.concat() |> Enum.concat() ==
               Enum.concat(subjects)
    end

    test "counts subjects that produce no statements towards the batch" do
      assert [full, partial] = batches(List.duplicate([], 501))
      assert length(full) == 500
      assert length(partial) == 1
    end

    test "cuts a batch short when its statements grow large" do
      big = String.duplicate("x", 200_000)

      # Five subjects of 200 kB reach the 1 MB cap well before the 500-subject
      # one, and the sixth starts a fresh batch.
      assert [first, second] = batches(List.duplicate([big], 6))
      assert length(first) == 5
      assert length(second) == 1
    end

    test "an empty stream yields no batches" do
      assert batches([]) == []
    end
  end

  defp batches(subjects), do: subjects |> Writer.batches() |> Enum.to_list()

  defp a(n), do: Writer.statement({@subject, "http://p", ~s("#{n}")})
end
