#!/usr/bin/env python3
"""Interactive browser for per-predicate triple differences between two stores.

Combines the two sibling scripts:

  * ``compare_predicate_counts.py`` builds the left-pane list of predicates
    whose triple counts differ between the two endpoints.
  * ``compare_predicate_triples.py`` fills the right pane with the actual
    triples that differ for the highlighted predicate.

Usage:
    ./compare_browser.py <endpoint_a> <endpoint_b>

Options:
    --timeout <secs>        Per-request timeout (default: very large).
    --ignore-prefix <iri>   Ignore predicates whose IRI starts with this prefix
                            (repeatable).

Keys:
    up/down, j/k     move the predicate selection (left pane)
    pgup/pgdn        scroll the triples (right pane)
    g / G            jump to top / bottom of the focused pane
    tab              toggle focus between panes
    r                reload triples for the selected predicate
    q                quit
"""

import argparse
import curses
import sys

import compare_predicate_counts as counts_mod
import compare_predicate_triples as triples_mod


def compute_diffs(endpoint_a, endpoint_b, timeout, ignore_prefixes):
    """Return (totals, diffs) where diffs is a list of (predicate, a, b)."""
    grand_total_a = counts_mod.fetch_total(endpoint_a, timeout)
    grand_total_b = counts_mod.fetch_total(endpoint_b, timeout)
    counts_a = counts_mod.fetch_counts(endpoint_a, timeout, ignore_prefixes)
    counts_b = counts_mod.fetch_counts(endpoint_b, timeout, ignore_prefixes)

    diffs = []
    for p in sorted(set(counts_a) | set(counts_b)):
        a = counts_a.get(p)
        b = counts_b.get(p)
        if a != b:
            diffs.append((p, a, b))

    totals = {
        "grand_a": grand_total_a,
        "grand_b": grand_total_b,
        "preds_a": len(counts_a),
        "preds_b": len(counts_b),
    }
    return totals, diffs


def triples_lines(endpoint_a, endpoint_b, predicate, timeout):
    """Return a list of display lines for the differing triples of a predicate."""
    pairs_a = triples_mod.fetch_pairs(endpoint_a, predicate, timeout)
    pairs_b = triples_mod.fetch_pairs(endpoint_b, predicate, timeout)

    only_a = sorted(
        (triples_mod.fmt_term(pairs_a[k][0]), triples_mod.fmt_term(pairs_a[k][1]))
        for k in set(pairs_a) - set(pairs_b)
    )
    only_b = sorted(
        (triples_mod.fmt_term(pairs_b[k][0]), triples_mod.fmt_term(pairs_b[k][1]))
        for k in set(pairs_b) - set(pairs_a)
    )

    lines = []
    lines.append(f"A: {len(pairs_a)} triples   B: {len(pairs_b)} triples")
    lines.append("")
    lines.append(f"--- Only in A (missing from B): {len(only_a)} ---")
    for s, o in only_a:
        lines.append(f"  {s}  {o}")
    lines.append("")
    lines.append(f"--- Only in B (missing from A): {len(only_b)} ---")
    for s, o in only_b:
        lines.append(f"  {s}  {o}")
    return lines


class Browser:
    LEFT = 0
    RIGHT = 1

    def __init__(self, stdscr, endpoint_a, endpoint_b, timeout, totals, diffs):
        self.stdscr = stdscr
        self.endpoint_a = endpoint_a
        self.endpoint_b = endpoint_b
        self.timeout = timeout
        self.totals = totals
        self.diffs = diffs

        self.sel = 0          # selected predicate index
        self.left_top = 0     # first visible predicate row
        self.right_top = 0    # first visible triples row
        self.focus = self.LEFT
        self.cache = {}       # predicate -> list[str] (or ["<error...>"])
        self.right_lines = []

    # -- data ---------------------------------------------------------------

    def load_triples(self, force=False):
        if not self.diffs:
            self.right_lines = ["(no differing predicates)"]
            return
        predicate = self.diffs[self.sel][0]
        if force:
            self.cache.pop(predicate, None)
        if predicate not in self.cache:
            self.draw(status="loading triples...")
            try:
                self.cache[predicate] = triples_lines(
                    self.endpoint_a, self.endpoint_b, predicate, self.timeout
                )
            except Exception as exc:  # noqa: BLE001 - surfaced in the pane
                self.cache[predicate] = [f"error: {exc}"]
        self.right_lines = self.cache[predicate]
        self.right_top = 0

    # -- rendering ----------------------------------------------------------

    def draw(self, status=None):
        scr = self.stdscr
        scr.erase()
        height, width = scr.getmaxyx()
        left_w = max(30, width // 2)
        right_x = left_w + 1
        right_w = width - right_x

        header = (
            f"A:{self.totals['grand_a']} ({self.totals['preds_a']}p)  "
            f"B:{self.totals['grand_b']} ({self.totals['preds_b']}p)  "
            f"delta {self.totals['grand_b'] - self.totals['grand_a']:+d}  "
            f"{len(self.diffs)} differing predicate(s)"
        )
        self._addstr(0, 0, header[: width - 1], curses.A_BOLD)

        footer = status or (
            "up/down select  pgup/pgdn scroll  tab focus  r reload  q quit"
        )
        self._addstr(height - 1, 0, footer[: width - 1], curses.A_DIM)

        body_top = 2
        body_h = height - body_top - 1

        # vertical separator
        for y in range(body_top, body_top + body_h):
            self._addstr(y, left_w, "|")

        self._draw_left(body_top, body_h, left_w)
        self._draw_right(body_top, body_h, right_x, right_w)

        scr.refresh()

    def _draw_left(self, top, h, w):
        # keep selection visible
        if self.sel < self.left_top:
            self.left_top = self.sel
        elif self.sel >= self.left_top + h:
            self.left_top = self.sel - h + 1

        for i in range(h):
            idx = self.left_top + i
            if idx >= len(self.diffs):
                break
            p, a, b = self.diffs[idx]
            a_s = "-" if a is None else str(a)
            b_s = "-" if b is None else str(b)
            label = f"{a_s:>8} {b_s:>8}  {p}"
            attr = curses.A_NORMAL
            if idx == self.sel:
                attr = curses.A_REVERSE if self.focus == self.LEFT else curses.A_BOLD
            self._addstr(top + i, 0, label[: w - 1], attr)

    def _draw_right(self, top, h, x, w):
        max_top = max(0, len(self.right_lines) - h)
        if self.right_top > max_top:
            self.right_top = max_top
        for i in range(h):
            idx = self.right_top + i
            if idx >= len(self.right_lines):
                break
            self._addstr(top + i, x, self.right_lines[idx][: w - 1])

    def _addstr(self, y, x, text, attr=curses.A_NORMAL):
        try:
            self.stdscr.addstr(y, x, text, attr)
        except curses.error:
            # writing to the last cell raises; ignore.
            pass

    # -- input loop ---------------------------------------------------------

    def visible_body_h(self):
        height = self.stdscr.getmaxyx()[0]
        return height - 3

    def run(self):
        self.load_triples()
        while True:
            self.draw()
            ch = self.stdscr.getch()
            if ch in (ord("q"), 27):  # q or ESC
                return
            elif ch == ord("\t"):
                self.focus = self.RIGHT if self.focus == self.LEFT else self.LEFT
            elif ch == ord("r"):
                self.load_triples(force=True)
            elif ch in (curses.KEY_UP, ord("k")):
                self._move(-1)
            elif ch in (curses.KEY_DOWN, ord("j")):
                self._move(1)
            elif ch in (curses.KEY_NPAGE,):
                self._scroll_right(self.visible_body_h())
            elif ch in (curses.KEY_PPAGE,):
                self._scroll_right(-self.visible_body_h())
            elif ch == ord("g"):
                self._jump(top=True)
            elif ch == ord("G"):
                self._jump(top=False)

    def _move(self, delta):
        if self.focus == self.LEFT:
            if not self.diffs:
                return
            new = min(max(self.sel + delta, 0), len(self.diffs) - 1)
            if new != self.sel:
                self.sel = new
                self.load_triples()
        else:
            self._scroll_right(delta)

    def _scroll_right(self, delta):
        self.right_top = max(0, self.right_top + delta)

    def _jump(self, top):
        if self.focus == self.LEFT:
            if not self.diffs:
                return
            self.sel = 0 if top else len(self.diffs) - 1
            self.load_triples()
        else:
            if top:
                self.right_top = 0
            else:
                self.right_top = max(0, len(self.right_lines) - self.visible_body_h())


def main():
    parser = argparse.ArgumentParser(
        description="Interactively browse per-predicate triple differences between two SPARQL endpoints."
    )
    parser.add_argument("endpoint_a", help="SPARQL query URL of the first store")
    parser.add_argument("endpoint_b", help="SPARQL query URL of the second store")
    parser.add_argument("--timeout", type=int, default=9000000, help="Per-request timeout in seconds")
    parser.add_argument(
        "--ignore-prefix",
        action="append",
        default=[],
        metavar="IRI",
        help="Ignore predicates whose IRI starts with this prefix (repeatable)",
    )
    args = parser.parse_args()

    ignore_prefixes = counts_mod.DEFAULT_IGNORE_PREFIXES + args.ignore_prefix

    print("Fetching predicate counts (this may take a while)...", file=sys.stderr)
    try:
        totals, diffs = compute_diffs(
            args.endpoint_a, args.endpoint_b, args.timeout, ignore_prefixes
        )
    except Exception as exc:
        print(f"error: failed to fetch counts: {exc}", file=sys.stderr)
        return 2

    if not diffs:
        print("MATCH: all predicate counts are identical.")
        return 0

    curses.wrapper(
        lambda scr: Browser(
            scr, args.endpoint_a, args.endpoint_b, args.timeout, totals, diffs
        ).run()
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
