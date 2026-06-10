defmodule CitationPopulatorTest do
  use ExUnit.Case, async: true

  alias CitationPopulator.{Agent, Citation, DataCite, Envri, Structured}

  defp dt(s) do
    {:ok, dt, _} = DateTime.from_iso8601(s)
    dt
  end

  describe "Citation.temporal_coverage_display/2" do
    test "daily object renders the middle date" do
      interval = {dt("2023-05-01T00:00:00Z"), dt("2023-05-02T00:00:00Z")}
      assert Citation.temporal_coverage_display(interval, 0) == "2023-05-01"
    end

    test "single calendar year renders as the year" do
      interval = {dt("2017-01-01T00:00:00Z"), dt("2018-01-01T00:00:00Z")}
      assert Citation.temporal_coverage_display(interval, 0) == "2017"
    end

    test "multiple calendar years render as a year range" do
      interval = {dt("2017-01-01T00:00:00Z"), dt("2023-01-01T00:00:00Z")}
      assert Citation.temporal_coverage_display(interval, 0) == "2017–2022"
    end

    test "generic interval renders as a date range" do
      interval = {dt("2017-04-25T06:00:00Z"), dt("2023-04-30T18:00:00Z")}
      assert Citation.temporal_coverage_display(interval, 0) == "2017-04-25–2023-04-30"
    end

    test "the UTC offset shifts the rendered dates (SITES, UTC+1)" do
      interval = {dt("2016-12-31T23:00:00Z"), dt("2022-12-31T23:00:00Z")}
      # at UTC+1 both ends are Jan 1 local, so the year-range branch fires;
      # without the offset this would render as a plain date range
      assert Citation.temporal_coverage_display(interval, 3600) == "2017–2022"
      assert Citation.temporal_coverage_display(interval, 0) == "2016-12-31–2022-12-31"
    end
  end

  describe "DataCite.parse_doi/1" do
    test "parses and uppercases the suffix" do
      assert DataCite.parse_doi("10.18160/v0ex-ab12") == {"10.18160", "V0EX-AB12"}
    end

    test "rejects non-DOIs" do
      assert DataCite.parse_doi("not-a-doi") == nil
      assert DataCite.parse_doi("11676/abc") == nil
      assert DataCite.parse_doi(nil) == nil
    end
  end

  describe "Agent.normalize_orcid/1" do
    test "extracts the short dashed id from a URL form" do
      assert Agent.normalize_orcid("https://orcid.org/0000-0002-1825-0097") ==
               "0000-0002-1825-0097"
    end

    test "drops ids with an invalid check character" do
      assert Agent.normalize_orcid("0000-0002-1825-0098") == nil
    end

    test "accepts X as check character position" do
      # 0000-0002-9079-593X is a valid ORCID (X check char)
      assert Agent.normalize_orcid("0000-0002-9079-593X") == "0000-0002-9079-593X"
    end
  end

  describe "Structured citations" do
    @person %{
      "self" => %{"uri" => "http://p", "comments" => []},
      "firstName" => "Jane",
      "lastName" => "Doe"
    }
    @org %{"self" => %{"uri" => "http://o", "comments" => []}, "name" => "Org Name"}

    @input %{
      pid_url: "https://hdl.handle.net/11676/abc",
      file_name: "f.csv",
      hash_id: "abc",
      authors: [@person, @org],
      title: "T",
      temp_cov: "2017–2022",
      year: "2023",
      note: nil,
      keywords: ["a", "b"],
      publisher: "ICOS ERIC",
      licence_url: "http://lic",
      doi_raw: nil,
      pid: "11676/abc"
    }

    test "to_bibtex" do
      assert Structured.to_bibtex(@input) ==
               "@misc{https://hdl.handle.net/11676/abc,\r\n" <>
                 "  author={Doe, Jane and Org Name},\r\n" <>
                 "  title={T, 2017–2022},\r\n" <>
                 "  year={2023},\r\n" <>
                 "  keywords={a, b},\r\n" <>
                 "  url={https://hdl.handle.net/11676/abc},\r\n" <>
                 "  publisher={ICOS ERIC},\r\n" <>
                 "  copyright={http://lic},\r\n" <>
                 "  pid={11676/abc}\r\n}"
    end

    test "to_ris" do
      assert Structured.to_ris(@input) ==
               "TY - DATA\r\nT1 - T, 2017–2022\r\nID - 11676/abc\r\nPY - 2023\r\n" <>
                 "UR - https://hdl.handle.net/11676/abc\r\nPB - ICOS ERIC\r\n" <>
                 "AU - Doe, Jane\r\nAU - Org Name\r\nKW - a\r\nKW - b\r\nER - "
    end
  end

  describe "DataCite.map_attributes/2" do
    test "maps DataCite attributes into the doi-core DoiMeta JSON shape" do
      attrs = %{
        "doi" => "10.18160/v0ex-ab12",
        "state" => "findable",
        "creators" => [
          %{
            "name" => "Doe, Jane",
            "givenName" => "Jane",
            "familyName" => "Doe",
            "nameIdentifiers" => [
              %{
                "nameIdentifier" => "0000-0002-1825-0097",
                "nameIdentifierScheme" => "ORCID",
                "schemeUri" => "https://orcid.org"
              }
            ],
            "affiliation" => ["Lund University"]
          },
          %{"name" => "ICOS ATC", "nameType" => "Organizational"}
        ],
        "titles" => [%{"title" => "A title", "lang" => nil}],
        "publisher" => "ICOS ERIC -- Carbon Portal",
        "publicationYear" => 2023,
        "types" => %{"resourceTypeGeneral" => "Dataset", "bibtex" => "misc"},
        "subjects" => [],
        "contributors" => [],
        "dates" => [%{"date" => "2023-05-01", "dateType" => "Issued"}],
        "formats" => ["text/csv"],
        "version" => "2",
        "descriptions" => [%{"description" => "d", "descriptionType" => "Abstract"}],
        "url" => "https://meta.icos-cp.eu/objects/abc",
        "fundingReferences" => [
          %{
            "funderName" => "EC",
            "funderIdentifierType" => "Crossref Funder ID",
            "schemeUri" => "https://x"
          }
        ],
        "geoLocations" => [
          %{"geoLocationPoint" => %{"pointLongitude" => "13.4", "pointLatitude" => 56.1}}
        ]
      }

      meta = DataCite.map_attributes(attrs, {"10.18160", "V0EX-AB12"})

      assert meta["doi"] == "10.18160/V0EX-AB12"
      assert meta["state"] == "findable"

      assert meta["creators"] == [
               %{
                 "givenName" => "Jane",
                 "familyName" => "Doe",
                 "nameType" => "Personal",
                 "nameIdentifiers" => [
                   %{
                     "nameIdentifier" => "0000-0002-1825-0097",
                     "nameIdentifierScheme" => "ORCID",
                     "schemeUri" => "http://orcid.org/"
                   }
                 ],
                 "affiliation" => [%{"name" => "Lund University"}]
               },
               %{
                 "name" => "ICOS ATC",
                 "nameType" => "Organizational",
                 "nameIdentifiers" => [],
                 "affiliation" => []
               }
             ]

      # lang: nil must be dropped, not serialized as null
      assert meta["titles"] == [%{"title" => "A title"}]
      # extra DataCite type keys are dropped
      assert meta["types"] == %{"resourceTypeGeneral" => "Dataset"}
      # version "2" does not match the doi-core "major.minor" format
      refute Map.has_key?(meta, "version")
      # the funding scheme URI key is capitalized in doi-core
      assert meta["fundingReferences"] == [
               %{
                 "funderName" => "EC",
                 "funderIdentifierType" => "Crossref Funder ID",
                 "SchemeURI" => "https://x"
               }
             ]

      # point coordinates are always present (null when missing) and numeric
      assert meta["geoLocations"] == [
               %{"geoLocationPoint" => %{"pointLongitude" => 13.4, "pointLatitude" => 56.1}}
             ]

      assert meta["dates"] == [%{"date" => "2023-05-01", "dateType" => "Issued"}]
      assert meta["formats"] == ["text/csv"]
      refute Map.has_key?(meta, "event")
      refute Map.has_key?(meta, "relatedIdentifiers")
    end
  end

  describe "Envri inference" do
    test "objects and collections by host + prefix" do
      assert Envri.object_envri("https://meta.icos-cp.eu/objects/abc") == :icos
      assert Envri.collection_envri("https://meta.icos-cp.eu/collections/abc") == :icos
      assert Envri.object_envri("https://meta.fieldsites.se/objects/abc") == :sites
      assert Envri.object_envri("https://citymeta.icos-cp.eu/objects/abc") == :icos_cities
    end

    test "unknown hosts and wrong prefixes yield nil" do
      assert Envri.object_envri("https://example.com/objects/abc") == nil
      assert Envri.object_envri("https://meta.icos-cp.eu/resources/stations/abc") == nil
      # the prefix check is against the https dataItemPrefix, like in Scala
      assert Envri.object_envri("http://meta.icos-cp.eu/objects/abc") == nil
    end
  end
end
