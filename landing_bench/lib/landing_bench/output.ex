defmodule LandingBench.Output do
  @moduledoc """
  Console output of benchmark events. Shared by live runs and the viewer of
  stored runs (`LandingBench.Viewer`), so that a replayed run looks like the
  live one.

  Events are maps with a `:type`:

    * `:portal` - the portal page was fetched (`:portal_url`, `:fetch_ms`, `:envri`, `:meta_host`)
    * `:secondary` - a secondary meta host is used (`:base_url`)
    * `:sparql_page` - a page of the search result list was fetched (`:offset`, `:count`, `:ms`)
    * `:object` - a landing page was fetched (`:index`, `:file_name`, `:uri`, `:primary`,
      `:secondary` and `:match`, the latter two `nil` without a secondary meta host)
  """

  alias LandingBench.Diff

  @doc """
  Prints `event`. With `show_diff`, a diff of the response bodies is printed for
  mismatching landing pages; this needs the `:body` of both responses.
  """
  def print(%{type: :portal} = e, _show_diff) do
    IO.puts(
      "Portal page #{e.portal_url} fetched in #{fmt_ms(e.fetch_ms)} " <>
        "(envri #{e.envri}, meta host #{e.meta_host})"
    )
  end

  def print(%{type: :secondary} = e, _show_diff),
    do: IO.puts("Secondary meta host #{e.base_url}")

  def print(%{type: :sparql_page} = e, _show_diff),
    do: IO.puts("SPARQL page at offset #{e.offset}: #{e.count} objects in #{fmt_ms(e.ms)}")

  def print(%{type: :object, secondary: nil} = e, _show_diff),
    do: print_result(pad(e.index), e.primary, "(#{e.file_name})")

  def print(%{type: :object} = e, show_diff) do
    print_result(pad(e.index), e.primary, "(#{e.file_name})")
    print_result(pad(""), e.secondary, "[#{match_label(e.match)}]")

    if show_diff and Diff.mismatch?(e.match),
      do: print_diff(e.primary, e.secondary)
  end

  defp print_result(prefix, %{error: nil} = r, suffix) do
    IO.puts(
      "#{prefix} #{r.status} #{String.pad_leading(fmt_ms(r.ms), 11)} " <>
        "#{String.pad_leading(Integer.to_string(r.bytes), 8)} B  #{r.url}  #{suffix}"
    )
  end

  defp print_result(prefix, r, suffix),
    do: IO.puts("#{prefix} ERR #{r.url}: #{r.error}  #{suffix}")

  # Diffs the bodies with known differences removed, as in `Diff.compare/2`, so
  # that only the differences that caused the mismatch are shown.
  defp print_diff(primary, secondary) do
    IO.puts(IO.ANSI.format([:red, "     --- #{primary.url}"]))
    IO.puts(IO.ANSI.format([:green, "     +++ #{secondary.url}"]))

    if is_binary(primary.body) and is_binary(secondary.body) do
      Diff.unified(
        Diff.ignore_known_differences(primary.body),
        Diff.ignore_known_differences(secondary.body)
      )
      |> Enum.each(fn line -> IO.puts(IO.ANSI.format([diff_color(line), "     ", line])) end)
    else
      IO.puts("     (response bodies not available)")
    end
  end

  defp diff_color("@@" <> _), do: :cyan
  defp diff_color("-" <> _), do: :red
  defp diff_color("+" <> _), do: :green
  defp diff_color(_), do: :reset

  def match_label(:identical), do: "match"
  def match_label(:identical_ignoring_known), do: "match (ignoring known differences)"
  def match_label(:status_differs), do: "MISMATCH: HTTP status differs"
  def match_label(:fetch_error), do: "not compared: fetch error"
  def match_label({:different, line}), do: "MISMATCH: first difference at line #{line}"

  defp pad(""), do: String.duplicate(" ", 5)
  defp pad(i), do: String.pad_leading("##{i}", 5)

  # `ms / 1` since timings read back from JSON may be integers
  def fmt_ms(ms), do: :erlang.float_to_binary(ms / 1, decimals: 1) <> " ms"

  def format_error(err) when is_binary(err), do: err
  def format_error(err) when is_exception(err), do: Exception.message(err)
  def format_error(err), do: inspect(err)
end
