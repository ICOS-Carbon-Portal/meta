defmodule LandingBench.Diff do
  @moduledoc "Comparison of landing page responses and line-based unified diff of two strings."

  @context 3

  @doc """
  Returns the unified diff hunks of `a` and `b` as a list of lines (without
  file headers), with `context` unchanged lines around each change.
  """
  @spec unified(String.t(), String.t(), non_neg_integer()) :: [String.t()]
  def unified(a, b, context \\ @context) do
    edits = numbered_edits(String.split(a, "\n"), String.split(b, "\n"))
    changed = for {{op, _, _, _}, i} <- Enum.with_index(edits), op != :eq, do: i

    changed
    |> hunk_ranges(context, length(edits))
    |> Enum.flat_map(fn {first, last} -> hunk(Enum.slice(edits, first..last)) end)
  end

  @doc """
  Compares two landing page responses (maps with `:status`, `:body` and `:error`).
  If the bodies differ, they are compared again with known environment-specific
  differences (see `ignore_known_differences/1`) removed.
  """
  def compare(%{error: nil} = a, %{error: nil} = b) do
    cond do
      a.status != b.status ->
        :status_differs

      a.body == b.body ->
        :identical

      true ->
        body_a = ignore_known_differences(a.body)
        body_b = ignore_known_differences(b.body)

        if body_a == body_b,
          do: :identical_ignoring_known,
          else: {:different, first_differing_line(body_a, body_b)}
    end
  end

  def compare(_a, _b), do: :fetch_error

  defp first_differing_line(a, b) do
    Enum.zip(String.split(a, "\n"), String.split(b, "\n"))
    |> Enum.find_index(fn {la, lb} -> la != lb end)
    |> case do
      # one body is a prefix of the other, line-wise
      nil -> min(length(String.split(a, "\n")), length(String.split(b, "\n"))) + 1
      idx -> idx + 1
    end
  end

  def mismatch?({:different, _line}), do: true
  def mismatch?(:status_differs), do: true
  def mismatch?(_match), do: false

  @doc """
  Removes known environment-specific differences (test host names, badges) from
  `text`, leaving everything else untouched.
  """
  @spec ignore_known_differences(String.t()) :: String.t()
  def ignore_known_differences(text) do
    Enum.reduce(replacements(), text, fn {pattern, replacement}, acc ->
      String.replace(acc, pattern, replacement)
    end)
  end

  # {pattern, replacement} pairs applied by `ignore_known_differences/1`. A function
  # rather than a module attribute, since compiled regexes cannot be stored in attributes.
  defp replacements do
    [
      {"fs4vm.", ""},
      # Test environment badge (e.g. on fs4vm): title prefix, styles and markup
      {"<title>🚧 ", "<title>"},
      {~r/<style>\s*\.env-border\b.*?<\/style>\n\n/s, ""},
      {~r/[ \t]*<div class="env-border"><\/div>\n[ \t]*\n/, ""},
      {~r/[ \t]*<div class="env-badge">[^<]*<\/div>\n[ \t]*\n/, ""},
      # Download and preview counts, which differ between hosts with separate logs
      {~r/(<label class="fw-bold">(?:Downloads|Previews)<\/label><\/div>\n<div [^>]*>\n\s*)\d+/, "\\1"},
      # Website Carbon badge, absent on some hosts
      {~r/[ \t]*<div id="wcb"[^>]*><\/div>\n[ \t]*<script[^>]*website-carbon-badges[^>]*><\/script>\n[ \t]*\n/,
       ""}
    ]
  end

  # Flattens the Myers edit script into one entry per line, each tagged with
  # the 1-based line numbers it has in `a` and `b` (or would have, if inserted).
  defp numbered_edits(lines_a, lines_b) do
    List.myers_difference(lines_a, lines_b)
    |> Enum.flat_map(fn {op, lines} -> Enum.map(lines, &{op, &1}) end)
    |> Enum.map_reduce({1, 1}, fn {op, line}, {ia, ib} ->
      next =
        case op do
          :eq -> {ia + 1, ib + 1}
          :del -> {ia + 1, ib}
          :ins -> {ia, ib + 1}
        end

      {{op, line, ia, ib}, next}
    end)
    |> elem(0)
  end

  # Index ranges of the edit list to show, merging changes whose context overlaps
  defp hunk_ranges(changed, context, n) do
    changed
    |> Enum.map(fn i -> {max(i - context, 0), min(i + context, n - 1)} end)
    |> Enum.reduce([], fn
      {first, last}, [{pfirst, plast} | rest] when first <= plast + 1 ->
        [{pfirst, max(last, plast)} | rest]

      range, acc ->
        [range | acc]
    end)
    |> Enum.reverse()
  end

  defp hunk([{_, _, ia, ib} | _] = edits) do
    len_a = Enum.count(edits, fn {op, _, _, _} -> op != :ins end)
    len_b = Enum.count(edits, fn {op, _, _, _} -> op != :del end)

    header = "@@ -#{ia},#{len_a} +#{ib},#{len_b} @@"
    [header | Enum.map(edits, &edit_line/1)]
  end

  defp edit_line({:eq, line, _, _}), do: " " <> line
  defp edit_line({:del, line, _, _}), do: "-" <> line
  defp edit_line({:ins, line, _, _}), do: "+" <> line
end
