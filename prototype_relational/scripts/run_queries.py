#!/usr/bin/env python3
"""
Script to extract and print SPARQL queries from unique_sparql.log.
Reads the log file, extracts each query, and calls a print function on each.
"""

def print_query(query, index):
    """Print a single SPARQL query with formatting."""
    print(f"\n{'='*80}")
    print(f"Query #{index}")
    print(f"{'='*80}")
    print(query.strip())

def run_query(query, host='http://localhost:65432/sparql'):
    """
    Execute a SPARQL query against an Ontop endpoint.

    Args:
        query: The SPARQL query string to execute
        host: The SPARQL endpoint URL (default: http://localhost:8080/sparql)

    Returns:
        A tuple of (success, response_text, error_message)
        - success: Boolean indicating if the request succeeded
        - response_text: The response text from the endpoint
        - error_message: Error message if failed, None otherwise
    """
    import requests

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


def extract_queries(input_file='unique_sparql.log'):
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


def main(input_file='unique_sparql.log'):
    """Main function to read queries and print each one."""
    print(f"Reading queries from: {input_file}")

    queries = extract_queries(input_file)

    print(f"\nFound {len(queries)} queries\n")

    # Track failed queries
    failed_queries = []

    # Call print function on each query
    for idx, query in enumerate(queries, start=1):
        print_query(query, idx)
        success, response_text, error_msg = run_query(query)

        # Print response length
        print(f"Response length: {len(response_text)} characters")

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

if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    else:
        input_file = 'unique_sparql.log'

    main(input_file)
