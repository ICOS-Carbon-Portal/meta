#!/usr/bin/env python3
"""
SPARQL query rewriting utilities.

This module provides functions to rewrite SPARQL query patterns into more efficient
or equivalent forms.
"""

import re
from typing import Optional, Tuple


def _extract_balanced_parens(text: str, start_pos: int) -> Tuple[str, int]:
    """
    Extract content within balanced parentheses starting from start_pos.

    Args:
        text: The text to search in
        start_pos: Position of the opening parenthesis

    Returns:
        Tuple of (content, end_position)
    """
    if start_pos >= len(text) or text[start_pos] != '(':
        return "", start_pos

    depth = 0
    i = start_pos

    while i < len(text):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                # Return content without the outer parentheses
                return text[start_pos + 1:i], i
        i += 1

    return "", start_pos


def rewrite_optional_pattern(query: str) -> str:
    """
    Rewrite SPARQL FILTER NOT EXISTS + UNION patterns into OPTIONAL + FILTER constructs.

    Transforms patterns like:
        {
            {FILTER NOT EXISTS {?subject property ?variable}}
            UNION
            {
                ?subject property ?variable
                FILTER (condition1 || condition2 || ...)
            }
        }

    Into:
        {
            OPTIONAL {?subject property ?variable}
            FILTER (!BOUND(?variable) || condition1 || condition2 || ...)
        }

    Args:
        query: The SPARQL query string to rewrite

    Returns:
        The rewritten query string with patterns transformed

    Example:
        >>> query = '''
        ... {
        ...     {FILTER NOT EXISTS {?dobj cpmeta:hasVariableName ?varName}}
        ...     UNION
        ...     {
        ...         ?dobj cpmeta:hasVariableName ?varName
        ...         FILTER (?varName = "co2" || ?varName = "ch4")
        ...     }
        ... }
        ... '''
        >>> result = rewrite_optional_pattern(query)
        >>> print(result)
        {
            OPTIONAL {?dobj cpmeta:hasVariableName ?varName}
            FILTER (!BOUND(?varName) || ?varName = "co2" || ?varName = "ch4")
        }
    """

    # Pattern to match the FILTER NOT EXISTS + UNION construct
    # This regex handles:
    # 1. Opening brace with optional whitespace
    # 2. {FILTER NOT EXISTS {triple pattern}}
    # 3. UNION keyword
    # 4. {triple pattern FILTER (...)}
    # 5. Closing brace
    # Note: We'll extract FILTER conditions manually to handle nested parentheses

    pattern = re.compile(
        r'(\s*)\{' +                           # Opening brace with leading whitespace
        r'\s*\{FILTER\s+NOT\s+EXISTS\s+\{' +   # {FILTER NOT EXISTS {
        r'([^}]+)' +                            # Triple pattern (group 2)
        r'\}\}' +                               # }}
        r'\s+UNION\s+' +                        # UNION
        r'\{' +                                 # {
        r'\s*([^}]+?)' +                        # Triple pattern again (group 3)
        r'\s+FILTER\s+',                        # FILTER keyword
        re.DOTALL | re.IGNORECASE
    )

    # Process matches manually to handle balanced parentheses
    result = []
    last_end = 0

    for match in pattern.finditer(query):
        # Add text before this match
        result.append(query[last_end:match.start()])

        leading_ws = match.group(1)
        not_exists_triple = match.group(2).strip()
        union_triple = match.group(3).strip()

        # Find the FILTER ( position and extract balanced parentheses
        filter_start = match.end()
        filter_conditions, filter_end = _extract_balanced_parens(query, filter_start)

        if not filter_conditions:
            # Failed to parse, keep original
            result.append(match.group(0))
            last_end = match.end()
            continue

        filter_conditions = filter_conditions.strip()

        # Verify closing braces after the FILTER
        remaining_start = filter_end + 1
        remaining = query[remaining_start:]

        # Skip whitespace and find closing braces
        ws_match = re.match(r'\s*\}\s*\}', remaining)
        if not ws_match:
            # Closing braces not found, keep original
            result.append(match.group(0))
            last_end = match.end()
            continue

        # Update last_end to after the closing braces
        last_end = remaining_start + ws_match.end()

        # Verify that both triple patterns are the same
        # Normalize whitespace for comparison
        normalized_not_exists = re.sub(r'\s+', ' ', not_exists_triple)
        normalized_union = re.sub(r'\s+', ' ', union_triple)

        if normalized_not_exists != normalized_union:
            # If patterns don't match, keep original
            result.append(query[match.start():last_end])
            continue

        # Extract the variable name from the triple pattern
        # Assuming the variable is the object (last element) of the triple
        triple_parts = not_exists_triple.split()
        if len(triple_parts) >= 3:
            variable = triple_parts[-1].rstrip('.')
        else:
            # Can't determine variable, keep original
            result.append(query[match.start():last_end])
            continue

        # Construct the rewritten pattern
        # Use the same indentation as the original
        indent = leading_ws

        # Calculate sub-indentation (add one level)
        # Try to detect if tabs or spaces are used
        if '\t' in indent:
            sub_indent = indent + '\t'
        else:
            # Count spaces and add 4 more (or use existing pattern)
            base_spaces = len(indent) - len(indent.lstrip(' '))
            sub_indent = indent + '    '

        rewritten = f'''{indent}{{
{sub_indent}OPTIONAL {{{not_exists_triple}}}
{sub_indent}FILTER (!BOUND({variable}) || {filter_conditions})
{indent}}}'''

        result.append(rewritten)

    # Add remaining text after last match
    result.append(query[last_end:])

    return ''.join(result)


def rewrite_query(query: str) -> str:
    """
    Apply all query rewriting transformations.

    This is a convenience function that applies multiple rewriting rules
    in sequence.

    Args:
        query: The SPARQL query string to rewrite

    Returns:
        The rewritten query string
    """
    query = rewrite_optional_pattern(query)
    # Add more rewriting functions here as needed
    return query


if __name__ == '__main__':
    # Example usage and testing
    test_query = '''
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>

select ?dobj where {
    ?dobj cpmeta:hasObjectSpec ?spec .
    ?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
    FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
    {
        {FILTER NOT EXISTS {?dobj cpmeta:hasVariableName ?varName}}
        UNION
        {
            ?dobj cpmeta:hasVariableName ?varName
            FILTER (?varName = "co2" || ?varName = "ch4" || ?varName = "co" || ?varName = "n2o")
        }
    }
}
order by desc(?submTime)
'''

    print("Original query:")
    print(test_query)
    print("\n" + "="*80 + "\n")
    print("Rewritten query:")
    print(rewrite_query(test_query))
