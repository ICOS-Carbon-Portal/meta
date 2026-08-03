defmodule BiblioMaterializer.Attribution do
  @moduledoc """
  Port of AttributionProvider.getAuthors: the persons with memberships at the
  acquisition station, filtered by theme-specific rules and time relevance,
  ordered by attribution weight (descending, unweighted last) then name.
  """

  alias BiblioMaterializer.{Agent, Cache, Rdf, Vocab}

  def authors(obj) do
    case obj.acq.station_uri do
      nil ->
        []

      station ->
        memberships(obj.cache, station)
        |> Enum.filter(&tc_filter(&1, obj))
        |> Enum.filter(&relevant?(&1, obj.acq.start, obj.acq.stop))
        |> Enum.sort_by(fn m ->
          {m.weight == nil, -(m.weight || 0), String.upcase(m.last_name), m.first_name}
        end)
        |> Enum.uniq_by(& &1.person_uri)
        |> Enum.map(fn m ->
          Agent.person_json(
            obj.cache,
            m.person_uri,
            m.person_label,
            m.first_name,
            m.last_name,
            m.email,
            m.orcid
          )
        end)
    end
  end

  # Every object acquired at a station shares that station's membership set
  # (the candidate authors), so read and shape it once per station per run;
  # the per-object theme/time filtering above stays live.
  defp memberships(cache, station_uri) do
    Cache.fetch(cache, {:memberships, station_uri}, fn -> memberships_uncached(station_uri) end)
  end

  defp memberships_uncached(station_uri) do
    Rdf.select("""
    SELECT * WHERE {
      ?memb cpmeta:atOrganization <#{station_uri}> .
      ?person cpmeta:hasMembership ?memb .
      ?memb cpmeta:hasRole ?role .
      ?person cpmeta:hasFirstName ?firstName .
      ?person cpmeta:hasLastName ?lastName .
      OPTIONAL { ?person cpmeta:hasEmail ?email }
      OPTIONAL { ?person cpmeta:hasOrcidId ?orcid }
      OPTIONAL { ?person rdfs:label ?personLabel }
      OPTIONAL { ?memb cpmeta:hasStartTime ?start }
      OPTIONAL { ?memb cpmeta:hasEndTime ?end }
      OPTIONAL { ?memb cpmeta:hasAttributionWeight ?weight }
      OPTIONAL { ?memb cpmeta:hasExtraRoleInfo ?extra }
    }
    """)
    |> Enum.uniq_by(&Rdf.val(&1, "memb"))
    |> Enum.map(fn r ->
      %{
        person_uri: Rdf.val(r, "person"),
        person_label: Rdf.val(r, "personLabel"),
        first_name: Rdf.val(r, "firstName"),
        last_name: Rdf.val(r, "lastName"),
        email: Rdf.val(r, "email"),
        orcid: Rdf.val(r, "orcid"),
        start: Rdf.parse_datetime(Rdf.val(r, "start")),
        stop: Rdf.parse_datetime(Rdf.val(r, "end")),
        weight: Rdf.parse_int(Rdf.val(r, "weight")),
        extra: Rdf.val(r, "extra")
      }
    end)
  end

  # Atmosphere-theme objects only credit weighted memberships, and only when
  # the membership's extra-role species list matches the object's columns or
  # spec label (missing extra info or columns passes).
  defp tc_filter(m, obj) do
    if obj.spec.theme == Vocab.atmo_theme() do
      m.weight != nil and species_ok?(m.extra, obj.columns, obj.spec.label)
    else
      true
    end
  end

  defp species_ok?(nil, _columns, _spec_label), do: true
  defp species_ok?(_extra, nil, _spec_label), do: true

  defp species_ok?(extra, columns, spec_label) do
    col_labels = Enum.map(columns, &String.downcase(&1.label))
    spec = String.downcase(spec_label || "")

    extra
    |> String.split(",")
    |> Enum.map(&(&1 |> String.trim() |> String.downcase()))
    |> Enum.any?(fn species ->
      Enum.any?(col_labels, &String.contains?(&1, species)) or String.contains?(spec, species)
    end)
  end

  # Membership must overlap the acquisition interval; missing bounds pass.
  defp relevant?(m, acq_start, acq_stop) do
    if acq_start && acq_stop do
      (m.start == nil or DateTime.compare(m.start, acq_stop) == :lt) and
        (m.stop == nil or DateTime.compare(m.stop, acq_start) == :gt)
    else
      true
    end
  end
end
