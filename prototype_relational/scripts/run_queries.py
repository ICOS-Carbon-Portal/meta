#!/usr/bin/env python3

import requests
from query_rewriter import rewrite_optional_pattern

def print_query(query, index):
    print(f"\n{'='*80}")
    print(f"Query #{index}")
    print(f"{'='*80}")
    print(query.strip())


def rewrite_query(query):
    # Apply distinct_keywords rewrite
    query = query.replace("select (cpmeta:distinct_keywords() as ?keywords)", "select ?spec")
    # TODO: Rewrite distinct_keywords() to something proper instead of dropping it

    # Apply FILTER NOT EXISTS + UNION -> OPTIONAL + FILTER rewrite
    query = rewrite_optional_pattern(query)

    return query

def run_query(query, host='http://localhost:65432/sparql'):
    headers = {
        'accept': 'application/csv',
        'content-type': 'application/sparql-query'
    }

    try:
        response = requests.post(host, data=query, headers=headers)

        # Check if the request was successful (status code 2xx)
        if response.status_code >= 200 and response.status_code < 300:
            return True, response.text, None
        else:
            return False, response.text, f"HTTP {response.status_code}"
    except Exception as e:
        return False, "", str(e)


def extract_queries(input_file):
    """
    Extract individual SPARQL queries from the log file.

    Queries are separated by lines of dashes (---) or equals signs (===).
    Returns a list of query strings.
    """
    queries = []
    current_query = []

    with open(input_file, 'r') as f:
        for line in f:
            stripped = line.rstrip()

            # Check if this is a separator line (all dashes or all equals)
            if stripped and (all(c == '-' for c in stripped) or all(c == '=' for c in stripped)):
                # If we have accumulated query lines, save the query
                if current_query:
                    query_text = '\n'.join(current_query)
                    if query_text.strip():  # Only add non-empty queries
                        queries.append(query_text)
                    current_query = []
            else:
                # Accumulate lines that are part of a query
                current_query.append(line.rstrip())

    # Don't forget the last query if file doesn't end with a separator
    if current_query:
        query_text = '\n'.join(current_query)
        if query_text.strip():
            queries.append(query_text)

    return queries


def main(input_file, output_file):
    print(f"Reading queries from: {input_file}")
    queries = extract_queries(input_file)

    print(f"\nFound {len(queries)} queries\n")

    # Track failed queries and rewritten queries
    failed_queries = []
    rewritten_queries = []
    small_result_queries = []

    # Call print function on each query
    for idx, query in enumerate(queries, start=1):
        query = rewrite_query(query)
        print_query(query, idx)

        rewritten_queries.append(f"Query #{idx}\n{'='*80}\n{query}\n{'='*80}\n")
        success, response_text, error_msg = run_query(query)

        # Print response length
        line_count = len(response_text.splitlines())
        print(f"Response: {line_count} lines, {len(response_text)} characters")

        # Track failures
        if not success:
            failed_queries.append({
                'index': idx,
                'query': query,
                'response': response_text,
                'error': error_msg
            })
            print(f"⚠️  Query FAILED: {error_msg}")
        else:
            print("✓ Query succeeded")
            # Track queries with 1 or fewer rows (CSV: header=1 line, +0 or 1 data rows = 1-2 lines)
            if line_count <= 2:
                small_result_queries.append(idx)

    # Save all rewritten queries to file
    with open(output_file, 'w') as f:
        f.write('\n'.join(rewritten_queries))
    print(f"\n✓ Saved {len(rewritten_queries)} rewritten queries to: {output_file}")

    print(f"\n{'='*80}")

    # Print failed query details
    if failed_queries:
        print(f"\n{'='*80}")
        print("FAILED QUERIES DETAILS")
        print(f"{'='*80}")
        for failed in failed_queries:
            print(f"\n{'─'*80}")
            print(f"Failed Query #{failed['index']}")
            print(f"Error: {failed['error']}")
            print(f"{'─'*80}")
            # print("Query:")
            # print(failed['query'].strip())
            print(f"\nResponse received:")
            print(failed['response'] if failed['response'] else "(empty response)")
            print(f"{'─'*80}")


    print(f"\n{'='*80}")
    print(f"Total queries processed: {len(queries)}")
    print(f"Failed queries: {len(failed_queries)}")
    print(f"Queries returning ≤1 row: {len(small_result_queries)}")
    if small_result_queries:
        print(f"Query indices with ≤1 row: {', '.join(map(str, small_result_queries))}")

if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    else:
        input_file = 'unique_sparql.log'

    main(input_file, 'rewritten_queries.txt')
    print("DONE")
