#!/usr/bin/env python3
"""
SPARQL query rewriting utilities.

This module provides functions to rewrite SPARQL query patterns into more efficient
or equivalent forms.
"""

import re
from typing import Optional, Tuple, List, Set


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


def _extract_variables(pattern: str) -> Set[str]:
    """
    Extract SPARQL variables (starting with ?) from a pattern.

    Args:
        pattern: A SPARQL graph pattern

    Returns:
        Set of variable names (including the ? prefix)
    """
    # Match variables: ?varName or ?var123
    var_pattern = re.compile(r'\?[a-zA-Z_][a-zA-Z0-9_]*')
    return set(var_pattern.findall(pattern))


def _extract_balanced_braces(text: str, start_pos: int) -> Tuple[str, int]:
    """
    Extract content within balanced braces starting from start_pos.

    Args:
        text: The text to search in
        start_pos: Position of the opening brace

    Returns:
        Tuple of (content, end_position)
    """
    if start_pos >= len(text) or text[start_pos] != '{':
        return "", start_pos

    depth = 0
    i = start_pos

    while i < len(text):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                # Return content without the outer braces
                return text[start_pos + 1:i], i
        i += 1

    return "", start_pos


def rewrite_union_to_optional(query: str) -> str:
    """
    Rewrite simple UNION patterns into OPTIONAL + FILTER constructs.

    Transforms patterns like:
        {
            {?subject property1 ?var1}
            UNION
            {?subject property2 ?var2}
        }

    Into:
        {
            OPTIONAL {?subject property1 ?var1}
            OPTIONAL {?subject property2 ?var2}
            FILTER (BOUND(?var1) || BOUND(?var2))
        }

    This rewrite is semantically approximate - it works best when the patterns
    are trying to match alternative ways to bind variables.

    Args:
        query: The SPARQL query string to rewrite

    Returns:
        The rewritten query string with patterns transformed

    Example:
        >>> query = '''
        ... {
        ...     {?coll cpmeta:hasDoi ?doi}
        ...     UNION
        ...     {?coll dcterms:hasPart ?dobj}
        ... }
        ... '''
        >>> result = rewrite_union_to_optional(query)
    """

    # Pattern to match simple UNION constructs (not FILTER NOT EXISTS ones)
    # We need to detect: { {pattern1} UNION {pattern2} }
    pattern = re.compile(
        r'(\s*)\{' +                    # Opening brace with leading whitespace (group 1)
        r'\s*\{' +                      # Inner opening brace
        r'(?!FILTER\s+NOT\s+EXISTS)' +  # Negative lookahead - not FILTER NOT EXISTS
        r'\s*',                         # Optional whitespace
        re.DOTALL | re.IGNORECASE
    )

    result = []
    last_end = 0

    for match in pattern.finditer(query):
        # Check if this is followed by a UNION
        # Extract the first pattern block
        pattern1_start = match.end()
        pattern1_content, pattern1_end = _extract_balanced_braces(query, pattern1_start - 1)

        if not pattern1_content:
            continue

        # Check for UNION after the first pattern
        remaining_start = pattern1_end + 1
        remaining = query[remaining_start:]
        union_match = re.match(r'\s*UNION\s*\{', remaining, re.IGNORECASE)

        if not union_match:
            continue

        # Extract the second pattern block
        pattern2_start = remaining_start + union_match.end() - 1
        pattern2_content, pattern2_end = _extract_balanced_braces(query, pattern2_start)

        if not pattern2_content:
            continue

        # Check for closing brace after pattern2
        final_remaining_start = pattern2_end + 1
        final_remaining = query[final_remaining_start:]
        closing_match = re.match(r'\s*\}', final_remaining)

        if not closing_match:
            continue

        # We found a valid { {pattern1} UNION {pattern2} } construct
        # Now determine the closing position
        last_end_pos = final_remaining_start + closing_match.end()

        # Add text before this match
        result.append(query[last_end:match.start()])
        last_end = last_end_pos

        # Extract variables from each pattern
        vars1 = _extract_variables(pattern1_content)
        vars2 = _extract_variables(pattern2_content)

        # Find variables that are distinct to each pattern (or primary in each)
        # For the FILTER, we want to check if at least one pattern matched
        # Strategy: use variables that are in one pattern but not the other, or appear as objects
        all_vars = vars1 | vars2
        unique_vars1 = vars1 - vars2
        unique_vars2 = vars2 - vars1

        # Build FILTER condition
        # If there are unique variables in each branch, use those
        # Otherwise, use all variables
        filter_vars = list(unique_vars1 | unique_vars2)

        if not filter_vars:
            # No unique variables, use all variables
            filter_vars = list(all_vars)

        if not filter_vars:
            # No variables at all, can't generate meaningful FILTER, skip rewrite
            result.append(query[match.start():last_end_pos])
            continue

        # Construct the rewritten pattern
        leading_ws = match.group(1)

        # Build FILTER clause with BOUND checks
        bound_checks = ' || '.join(f'BOUND({var})' for var in sorted(filter_vars))

        # Don't add outer braces - just replace with the OPTIONAL statements
        rewritten = f'''{leading_ws}OPTIONAL {{{pattern1_content}}}
{leading_ws}OPTIONAL {{{pattern2_content}}}
{leading_ws}FILTER ({bound_checks})'''

        result.append(rewritten)

    # Add remaining text after last match
    result.append(query[last_end:])

    return ''.join(result)


def rewrite_exists_to_optional(query: str) -> str:
    """
    Rewrite BIND(EXISTS{...} AS ?var) to OPTIONAL + BOUND pattern.

    Transforms:
        BIND(EXISTS{?x prop ?y} AS ?var)

    Into:
        OPTIONAL {?x prop ?y AS ?varCheck}
        BIND(BOUND(?varCheck) AS ?var)

    Args:
        query: The SPARQL query string to rewrite

    Returns:
        The rewritten query string
    """
    # Pattern to match BIND(EXISTS{...} AS ?var)
    pattern = re.compile(
        r'(\s*)BIND\s*\(\s*EXISTS\s*\{([^}]+)\}\s+AS\s+(\?[a-zA-Z_][a-zA-Z0-9_]*)\s*\)',
        re.IGNORECASE | re.DOTALL
    )

    def replace_exists(match):
        leading_ws = match.group(1)
        exists_pattern = match.group(2).strip()
        result_var = match.group(3)

        # Create a check variable name
        check_var = result_var + 'Check'

        # Replace blank nodes [] with the check variable
        # This allows us to check if the pattern exists
        optional_pattern = exists_pattern.replace('[]', check_var)

        # Build the replacement
        replacement = f'''{leading_ws}OPTIONAL {{ {optional_pattern} }}
{leading_ws}BIND(BOUND({check_var}) AS {result_var})'''

        return replacement

    return pattern.sub(replace_exists, query)


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
    # First, rewrite FILTER NOT EXISTS + UNION patterns
    query = rewrite_optional_pattern(query)

    # Then, rewrite remaining simple UNION patterns
    query = rewrite_union_to_optional(query)

    # Finally, rewrite EXISTS in BIND statements
    query = rewrite_exists_to_optional(query)

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
