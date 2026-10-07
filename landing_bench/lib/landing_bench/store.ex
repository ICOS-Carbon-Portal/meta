defmodule LandingBench.Store do
  @moduledoc """
  Stores benchmark runs in a local directory, so that they can be viewed again
  later (see `LandingBench.Viewer`). Several runs can share a directory:

      DIR/index.json                     all runs, as in their run.json
      DIR/RUN_ID/run.json                host, options, start/finish time, status, key figures
      DIR/RUN_ID/events.jsonl            the run's events (see `LandingBench.Output`),
                                         one JSON object per line, written as they happen
      DIR/RUN_ID/bodies/SHA256.html.gz   landing page bodies, gzipped and stored once per
                                         distinct body (only for runs with a secondary meta host)

  Run IDs are the UTC start time, e.g. `20261007-142355`. Since events are written
  as they happen, an interrupted run can be viewed too; its status stays `running`.
  """

  alias LandingBench.{Output, Stats}

  defstruct [:dir, :run_dir, :events, :meta]

  @doc "Creates the directory of a new run in `dir` and adds it to the index."
  def open(dir, opts) do
    started = DateTime.utc_now() |> DateTime.truncate(:second)
    id = unique_id(dir, Calendar.strftime(started, "%Y%m%d-%H%M%S"))
    run_dir = Path.join(dir, id)
    File.mkdir_p!(run_dir)

    meta = %{
      id: id,
      status: "running",
      error: nil,
      started_at: DateTime.to_iso8601(started),
      finished_at: nil,
      host: opts[:host],
      secondary: opts[:secondary],
      options: Map.new(Keyword.take(opts, [:max, :delay_ms, :jitter_ms, :timeout_ms, :insecure])),
      overview: nil
    }

    store = %__MODULE__{
      dir: dir,
      run_dir: run_dir,
      events: File.open!(Path.join(run_dir, "events.jsonl"), [:write, :binary]),
      meta: meta
    }

    save_meta(store)
    store
  end

  defp unique_id(dir, id, n \\ 1) do
    candidate = if n == 1, do: id, else: "#{id}-#{n}"
    if File.exists?(Path.join(dir, candidate)), do: unique_id(dir, id, n + 1), else: candidate
  end

  @doc "Appends `event` to the run's event log, storing response bodies if there are two to compare."
  def record(store, event) do
    line =
      event
      |> encode_event(store.run_dir)
      |> Map.put(:at, DateTime.utc_now() |> DateTime.to_iso8601())
      |> JSON.encode!()

    IO.binwrite(store.events, line <> "\n")
  end

  @doc "Marks the run as completed or failed, with the key figures of its results."
  def finish(store, outcome) do
    File.close(store.events)

    {status, error} =
      case outcome do
        {:ok, _results} -> {"completed", nil}
        {:error, err} -> {"failed", Output.format_error(err)}
      end

    # From the event log rather than `outcome`, so that failed runs get key figures too
    {:ok, _meta, events} = load_run(store.run_dir)

    meta = %{
      store.meta
      | status: status,
        error: error,
        finished_at: DateTime.utc_now() |> DateTime.truncate(:second) |> DateTime.to_iso8601(),
        overview: Stats.overview(for(%{type: :object} = e <- events, do: Stats.result(e)))
    }

    save_meta(%{store | meta: meta})
    IO.puts("\nStored run #{meta.id} in #{store.dir}")
  end

  defp save_meta(store) do
    # Round trip through JSON, so that the entry has string keys like the rest of the index
    entry = store.meta |> JSON.encode!() |> JSON.decode!()
    write_json(Path.join(store.run_dir, "run.json"), entry)

    runs =
      case list(store.dir) do
        {:ok, runs} -> Enum.reject(runs, &(&1["id"] == entry["id"]))
        {:error, _} -> []
      end

    write_json(Path.join(store.dir, "index.json"), %{
      "runs" => Enum.sort_by([entry | runs], & &1["id"])
    })
  end

  # Written to a temporary file first, so that an interrupted write cannot corrupt the index
  defp write_json(path, data) do
    tmp = path <> ".tmp"
    File.write!(tmp, JSON.encode_to_iodata!(data))
    File.rename!(tmp, path)
  end

  @doc "The runs in the index of `dir`, oldest first, as maps with string keys."
  def list(dir) do
    path = Path.join(dir, "index.json")

    with {:ok, json} <- File.read(path),
         {:ok, %{"runs" => runs}} <- JSON.decode(json) do
      {:ok, runs}
    else
      {:error, :enoent} -> {:error, "no stored runs in #{dir} (#{path} not found)"}
      _ -> {:error, "could not read #{path}"}
    end
  end

  @doc """
  Loads run `id` (or the most recent run if `id` is `"latest"`) from `dir`.
  Returns the run's metadata (string keys), its directory and its events.
  """
  def load(dir, id) do
    with {:ok, runs} <- list(dir),
         {:ok, id} <- resolve_id(runs, id),
         run_dir = Path.join(dir, id),
         {:ok, meta, events} <- load_run(run_dir) do
      {:ok, meta, run_dir, events}
    end
  end

  defp resolve_id([], "latest"), do: {:error, "no stored runs"}
  defp resolve_id(runs, "latest"), do: {:ok, List.last(runs)["id"]}

  defp resolve_id(runs, id) do
    if Enum.any?(runs, &(&1["id"] == id)),
      do: {:ok, id},
      else: {:error, "no run #{id} in the index"}
  end

  defp load_run(run_dir) do
    with {:ok, json} <- File.read(Path.join(run_dir, "run.json")),
         {:ok, meta} <- JSON.decode(json),
         {:ok, log} <- File.read(Path.join(run_dir, "events.jsonl")) do
      events =
        log
        |> String.split("\n", trim: true)
        # The last line may be incomplete if the run was interrupted
        |> Enum.flat_map(fn line ->
          case JSON.decode(line) do
            {:ok, event} -> [decode_event(event)]
            {:error, _} -> []
          end
        end)

      {:ok, meta, events}
    else
      {:error, err} -> {:error, "could not read run in #{run_dir}: #{inspect(err)}"}
    end
  end

  @doc "The landing page body with the given hash stored for the run in `run_dir`, or `nil`."
  def read_body(_run_dir, nil), do: nil

  def read_body(run_dir, sha) do
    case File.read(body_path(run_dir, sha)) do
      {:ok, gz} -> :zlib.gunzip(gz)
      {:error, _} -> nil
    end
  end

  defp body_path(run_dir, sha), do: Path.join([run_dir, "bodies", sha <> ".html.gz"])

  # Bodies are only needed to diff or recompare responses, so are only kept
  # when there are two responses to compare.
  defp encode_event(%{type: :object} = e, run_dir) do
    keep_bodies = e.secondary != nil

    e
    |> Map.merge(%{
      primary: encode_result(e.primary, keep_bodies, run_dir),
      secondary: e.secondary && encode_result(e.secondary, keep_bodies, run_dir)
    })
    |> Map.merge(encode_match(e.match))
  end

  defp encode_event(event, _run_dir), do: event

  defp encode_result(result, keep_body, run_dir) do
    sha = if keep_body and is_binary(result.body), do: save_body(result.body, run_dir)
    result |> Map.delete(:body) |> Map.put(:body_sha256, sha)
  end

  defp save_body(body, run_dir) do
    sha = :crypto.hash(:sha256, body) |> Base.encode16(case: :lower)
    path = body_path(run_dir, sha)

    unless File.exists?(path) do
      File.mkdir_p!(Path.dirname(path))
      File.write!(path, :zlib.gzip(body))
    end

    sha
  end

  defp encode_match(nil), do: %{match: nil}
  defp encode_match({:different, line}), do: %{match: "different", first_diff_line: line}
  defp encode_match(match), do: %{match: Atom.to_string(match)}

  defp decode_event(%{"type" => "portal"} = e) do
    %{
      type: :portal,
      portal_url: e["portal_url"],
      fetch_ms: e["fetch_ms"],
      envri: e["envri"],
      meta_host: e["meta_host"]
    }
  end

  defp decode_event(%{"type" => "secondary"} = e),
    do: %{type: :secondary, base_url: e["base_url"]}

  defp decode_event(%{"type" => "sparql_page"} = e),
    do: %{type: :sparql_page, offset: e["offset"], count: e["count"], ms: e["ms"]}

  defp decode_event(%{"type" => "object"} = e) do
    %{
      type: :object,
      index: e["index"],
      file_name: e["file_name"],
      uri: e["uri"],
      primary: decode_result(e["primary"]),
      secondary: e["secondary"] && decode_result(e["secondary"]),
      match: decode_match(e["match"], e["first_diff_line"])
    }
  end

  defp decode_result(r) do
    %{
      url: r["url"],
      status: r["status"],
      ms: r["ms"],
      bytes: r["bytes"],
      error: r["error"],
      body_sha256: r["body_sha256"]
    }
  end

  defp decode_match(nil, _line), do: nil
  defp decode_match("different", line), do: {:different, line}
  defp decode_match("identical", _), do: :identical
  defp decode_match("identical_ignoring_known", _), do: :identical_ignoring_known
  defp decode_match("status_differs", _), do: :status_differs
  defp decode_match("fetch_error", _), do: :fetch_error
end
