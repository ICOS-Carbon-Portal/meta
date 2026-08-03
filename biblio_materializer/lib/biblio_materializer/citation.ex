defmodule BiblioMaterializer.Citation do
  @moduledoc """
  Structural citation assembly for objects without a DOI citation, port of
  LiveCitationMaker.getIcosCitation/getSitesCitation/getDocCitation and
  CitationMaker's temporal-coverage helpers.

  All functions return a citation-info map:
  %{authors, title, year, temp_cov, cit_text} (authors is a list or nil).
  """

  alias BiblioMaterializer.{Agent, Attribution, Envri, Vocab}

  def icos_citation(obj, envri, pid_url) do
    offset = Envri.utc_offset(envri)
    temp_cov = temporal_coverage_display(interval(obj), offset)

    is_icos_project = obj.spec.project_uri == Vocab.icos_project()
    is_misc_project = obj.spec.project_uri == Vocab.misc_project()
    station_ts? = obj.spec.dataset_type == :station_time_series
    icos_like? = station_ts? and Vocab.icos_like_station?(obj.acq.station_types)

    title = icos_title(obj, station_ts?)
    production_agents = production_agents(obj)

    authors =
      if icos_like? and is_integer(obj.spec.data_level) and obj.spec.data_level < 3 do
        attribution = Attribution.authors(obj)

        cond do
          is_icos_project -> attribution
          Enum.any?(production_agents, &Agent.person?/1) -> production_agents
          true -> attribution
        end
      else
        production_agents
      end

    year = production_year(obj, offset)

    project_opt =
      cond do
        is_icos_project -> "ICOS RI"
        is_misc_project -> nil
        true -> obj.spec.project_label
      end

    project = if project_opt, do: project_opt <> ",", else: ""

    cit_text =
      if title && pid_url && temp_cov && year do
        authors_str = Enum.map_join(authors, ", ", &Agent.format_short/1)
        "#{authors_str} (#{year}). #{title}, #{temp_cov}, #{project} #{pid_url}"
      end

    %{authors: authors, title: title, year: year, temp_cov: temp_cov, cit_text: cit_text}
  end

  defp icos_title(obj, true = _station_ts?) do
    with label when is_binary(label) <- obj.spec.label,
         station when is_binary(station) <- obj.acq.station_name do
      vars =
        if obj.spec.uri == Vocab.atm_ghg_spec() do
          case obj.columns do
            nil ->
              ""

            cols ->
              labels = cols |> Enum.filter(& &1.has_unit) |> Enum.map(& &1.label)
              " (" <> Enum.join(labels, ", ") <> ")"
          end
        else
          ""
        end

      height =
        case obj.acq.sampling_height do
          nil -> ""
          h -> " (#{format_float(h)} m)"
        end

      "#{label}#{vars} from #{station}#{height}"
    else
      _ -> nil
    end
  end

  defp icos_title(obj, false = _station_ts?), do: obj.l3.title

  def sites_citation(obj, pid_url) do
    offset = Envri.utc_offset(:sites)
    temp_cov = temporal_coverage_display(interval(obj), offset)
    year = obj.subm.stop && year(obj.subm.stop, offset)
    station_ts? = obj.spec.dataset_type == :station_time_series

    title =
      if station_ts? do
        with label when is_binary(label) <- obj.spec.label,
             location when is_binary(location) <- obj.acq.site_location_label do
          data_type = label |> String.split(",") |> hd()
          "#{data_type} from #{obj.acq.sampling_point_label || location}"
        else
          _ -> nil
        end
      else
        obj.l3.title
      end

    authors_prefix = if station_ts?, do: "#{obj.acq.station_name} ", else: ""

    cit_text =
      if year && title && temp_cov && pid_url do
        "#{authors_prefix}(#{year}). #{title}, #{temp_cov} [Data set]. " <>
          "#{Envri.long_name(:sites)} (#{Envri.short_name(:sites)}). #{pid_url}"
      end

    # SITES citations carry no author list in the references
    %{authors: nil, title: title, year: year, temp_cov: temp_cov, cit_text: cit_text}
  end

  def doc_citation(obj, envri, authors, pid_url) do
    offset = Envri.utc_offset(envri)
    year = obj.subm.stop && year(obj.subm.stop, offset)

    formatted = authors |> Enum.uniq() |> Enum.map(&Agent.format_short/1)
    author_string = if formatted != [], do: Enum.join(formatted, ", ")

    title = obj.doc_title || obj.file_name

    cit_text =
      if year && title && pid_url do
        case envri do
          :sites ->
            "#{author_string || Envri.short_name(:sites)} (#{year}). #{title}. " <>
              "#{Envri.long_name(:sites)} (#{Envri.short_name(:sites)}). #{pid_url}"

          _ ->
            "#{author_string || "ICOS RI"}, #{year}. #{title}, #{pid_url}"
        end
      end

    %{authors: authors, title: obj.doc_title, year: year, temp_cov: nil, cit_text: cit_text}
  end

  defp production_agents(obj) do
    if obj.prod.exists do
      creator = obj.prod.creator_uri && Agent.read(obj.cache, obj.prod.creator_uri)
      contributors = Agent.read_contributors(obj.cache, obj.prod.contributor_uris)

      cond do
        creator == nil -> contributors
        Enum.any?(contributors, &(Agent.uri(&1) == Agent.uri(creator))) -> contributors
        true -> [creator | contributors]
      end
    else
      []
    end
  end

  # productionTime: the production end time, else the acquisition stop for
  # station time series.
  defp production_year(obj, offset) do
    cond do
      obj.prod.date_time -> year(obj.prod.date_time, offset)
      obj.spec.dataset_type == :station_time_series and obj.acq.stop -> year(obj.acq.stop, offset)
      true -> nil
    end
  end

  def interval(obj) do
    case obj.spec.dataset_type do
      :spatio_temporal -> if obj.l3.start && obj.l3.stop, do: {obj.l3.start, obj.l3.stop}
      _ -> if obj.acq.start && obj.acq.stop, do: {obj.acq.start, obj.acq.stop}
    end
  end

  @doc "Port of CitationMaker.getTimeFromInterval. offset = the ENVRI's fixed UTC offset in seconds."
  def temporal_coverage_display(nil, _offset), do: nil

  def temporal_coverage_display({start, stop}, offset) do
    seconds = DateTime.diff(stop, start, :second)

    cond do
      seconds < 24 * 3601 ->
        middle_ms =
          div(DateTime.to_unix(start, :millisecond) + DateTime.to_unix(stop, :millisecond), 2)

        format_date(DateTime.from_unix!(middle_ms, :millisecond), offset)

      day_of_year(start, offset) == 1 and day_of_year(stop, offset) == 1 ->
        start_year = local_date(start, offset).year
        stop_year = local_date(stop, offset).year

        if start_year == stop_year - 1,
          do: "#{start_year}",
          else: "#{start_year}–#{stop_year - 1}"

      is_midnight?(start, offset) and is_midnight?(stop, offset) ->
        to = DateTime.add(stop, -86_400, :second)
        "#{format_date(start, offset)}–#{format_date(to, offset)}"

      true ->
        "#{format_date(start, offset)}–#{format_date(stop, offset)}"
    end
  end

  def format_date(dt, offset), do: dt |> local_date(offset) |> Date.to_iso8601()

  def year(dt, offset), do: dt |> format_date(offset) |> String.slice(0, 4)

  defp local_date(dt, offset), do: dt |> DateTime.add(offset, :second) |> DateTime.to_date()

  defp day_of_year(dt, offset), do: dt |> local_date(offset) |> Date.day_of_year()

  defp is_midnight?(dt, offset),
    do: dt |> DateTime.add(offset, :second) |> DateTime.to_time() == ~T[00:00:00]

  # Mirrors Scala's Float rendering for sampling heights (150.0 -> "150.0").
  defp format_float(f), do: :erlang.float_to_binary(f, [:short])
end
