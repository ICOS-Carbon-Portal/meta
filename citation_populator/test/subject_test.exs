defmodule CitationPopulator.SubjectTest do
  use ExUnit.Case, async: true

  alias CitationPopulator.{Cache, Subject}

  @a "https://meta.icos-cp.eu/objects/a"
  @b "https://meta.icos-cp.eu/objects/b"
  @c "https://meta.icos-cp.eu/objects/c"

  describe "index/3 for a :row group" do
    test "keeps the first solution per subject, as a LIMIT 1 read would" do
      rows = [
        row(s: @a, title: "first"),
        row(s: @a, title: "second"),
        row(s: @b, title: "b")
      ]

      index = Subject.index(rows, :row, [@a, @b])

      assert index[@a] == row(s: @a, title: "first")
      assert index[@b] == row(s: @b, title: "b")
    end

    test "gives subjects the query returned nothing for a nil row" do
      index = Subject.index([row(s: @a, title: "a")], :row, [@a, @b, @c])

      assert index[@b] == nil
      assert index[@c] == nil
    end
  end

  describe "index/3 for a :value group" do
    test "keeps the first value per subject" do
      rows = [row(s: @a, v: "one"), row(s: @a, v: "two")]

      assert Subject.index(rows, :value, [@a, @b]) == %{@a => "one", @b => nil}
    end
  end

  describe "index/3 for a :values group" do
    test "collects every value per subject in query order, uniquely" do
      rows = [
        row(s: @a, v: "one"),
        row(s: @b, v: "only"),
        row(s: @a, v: "two"),
        row(s: @a, v: "one")
      ]

      assert Subject.index(rows, :values, [@a, @b, @c]) == %{
               @a => ["one", "two"],
               @b => ["only"],
               @c => []
             }
    end
  end

  describe "an entry's lifetime" do
    setup context do
      # Cache is named after its module by default, which this suite's other
      # tests also start; name this one after the test instead.
      name = :"cache_#{context.test |> to_string() |> String.replace(" ", "_")}"
      %{cache: Cache.table(start_supervised!({Cache, name: name}))}
    end

    test "fetch/3 answers from the prefetched entry without reading", %{cache: cache} do
      Cache.put(cache, {:data_core, @a}, row(s: @a, title: "prefetched"))

      assert Subject.fetch(cache, :data_core, @a) == row(s: @a, title: "prefetched")
    end

    test "a prefetched nil is an answer, not a miss", %{cache: cache} do
      # Absent per-subject data has to stay cached as nil, or every subject
      # without an acquisition would fall back to reading it again.
      Cache.put(cache, {:acq, @a}, nil)

      assert Subject.fetch(cache, :acq, @a) == nil
    end

    test "forget/2 drops a batch's entries and leaves reference data alone", %{cache: cache} do
      Cache.put(cache, {:data_core, @a}, row(s: @a, title: "a"))
      Cache.put(cache, {:contributors, @a}, ["someone"])
      Cache.put(cache, {:spec, "https://meta.icos-cp.eu/spec"}, :reference_data)

      Subject.forget(cache, [{@a, "https://meta.icos-cp.eu/ontologies/cpmeta/DataObject"}])

      assert Cache.get(cache, {:data_core, @a}) == :miss
      assert Cache.get(cache, {:contributors, @a}) == :miss
      assert Cache.get(cache, {:spec, "https://meta.icos-cp.eu/spec"}) == {:ok, :reference_data}
    end
  end

  defp row(bindings) do
    Map.new(bindings, fn {name, value} -> {to_string(name), %{"value" => value}} end)
  end
end
