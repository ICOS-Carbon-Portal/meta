#!/usr/bin/env python3

import argparse
import csv
import difflib
import io
import requests
import sys
import time

def print_query(query, index):
    print(f"\n{'='*80}")
    print(f"Query #{index}")
    print(f"{'='*80}")
    # print(query.strip())

def rewrite_distinct_keywords(query):
    # Apply distinct_keywords rewrite
    return query.replace("select (cpmeta:distinct_keywords() as ?keywords)", "select ?spec")

def run_query(query, host='http://localhost:65432/sparql', accept_header='application/csv'):
    headers = {
        'accept': accept_header,
        'content-type': 'application/sparql-query'
    }

    try:
        start_time = time.time()
        response = requests.post(host, data=query, headers=headers)
        elapsed_time = time.time() - start_time

        # print(response.text)


        # Check if the request was successful (status code 2xx)
        if response.status_code >= 200 and response.status_code < 300:
            return True, response.text, None, elapsed_time
        else:
            return False, response.text, f"HTTP {response.status_code}", elapsed_time
    except Exception as e:
        return False, "", str(e), 0


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


def print_diff(csv1, csv2, max_lines=50):
    """
    Print set-based differences between two CSV strings (order-independent).

    Args:
        csv1: First CSV string
        csv2: Second CSV string
        max_lines: Maximum number of difference lines to display per section (default: 50)
    """
    print(f"\n{'─'*80}")
    print("Set-based Diff (order-independent, ignoring rows with meta.fieldsites.se):")
    print(f"{'─'*80}")

    try:
        # Parse both CSV strings
        reader1 = csv.reader(io.StringIO(csv1))
        reader2 = csv.reader(io.StringIO(csv2))

        # Convert to lists to get all rows
        rows1 = list(reader1)
        rows2 = list(reader2)

        # Handle empty results
        if len(rows1) == 0 and len(rows2) == 0:
            print("Both results are empty")
            print(f"{'─'*80}")
            return

        # Get headers
        header1 = rows1[0] if len(rows1) > 0 else []
        header2 = rows2[0] if len(rows2) > 0 else []

        # Count total data rows (excluding header)
        total_rows1 = len(rows1) - 1 if len(rows1) > 1 else 0
        total_rows2 = len(rows2) - 1 if len(rows2) > 1 else 0

        # Convert data rows to sets of tuples (skip header)
        # Filter out rows containing 'meta.fieldsites.se'
        data1 = set(
            tuple(cell.strip() for cell in row)
            for row in rows1[1:]
            if not any('meta.fieldsites.se' in cell for cell in row)
        ) if len(rows1) > 1 else set()

        data2 = set(
            tuple(cell.strip() for cell in row)
            for row in rows2[1:]
            if not any('meta.fieldsites.se' in cell for cell in row)
        ) if len(rows2) > 1 else set()

        # Count filtered rows
        filtered1 = total_rows1 - len(data1)
        filtered2 = total_rows2 - len(data2)

        # Calculate set differences
        only_in_1 = data1 - data2
        only_in_2 = data2 - data1
        in_both = data1 & data2

        # Display statistics
        if filtered1 > 0 or filtered2 > 0:
            print(f"Filtered rows (containing meta.fieldsites.se):")
            print(f"  Endpoint 1: {filtered1} rows filtered")
            print(f"  Endpoint 2: {filtered2} rows filtered")
            print()
        print(f"Rows only in Endpoint 1: {len(only_in_1)}")
        print(f"Rows only in Endpoint 2: {len(only_in_2)}")
        print(f"Rows in both: {len(in_both)}")

        # Show rows only in Endpoint 1
        if only_in_1:
            print(f"\n--- Rows ONLY in Endpoint 1 ({len(only_in_1)} rows) ---")
            for i, row in enumerate(sorted(only_in_1)[:max_lines], 1):
                print(f"  {i}. {','.join(row)}")
            if len(only_in_1) > max_lines:
                print(f"  ... ({len(only_in_1) - max_lines} more rows not shown)")

        # Show rows only in Endpoint 2
        if only_in_2:
            print(f"\n+++ Rows ONLY in Endpoint 2 ({len(only_in_2)} rows) +++")
            for i, row in enumerate(sorted(only_in_2)[:max_lines], 1):
                print(f"  {i}. {','.join(row)}")
            if len(only_in_2) > max_lines:
                print(f"  ... ({len(only_in_2) - max_lines} more rows not shown)")

    except Exception as e:
        print(f"Error parsing CSV for diff: {e}")
        print("Falling back to line-by-line comparison:")
        # Fall back to simple line diff
        lines1 = set(csv1.splitlines())
        lines2 = set(csv2.splitlines())
        only_1 = lines1 - lines2
        only_2 = lines2 - lines1
        if only_1:
            print(f"\nOnly in Endpoint 1: {len(only_1)} lines")
            for line in list(only_1)[:max_lines]:
                print(f"  - {line}")
        if only_2:
            print(f"\nOnly in Endpoint 2: {len(only_2)} lines")
            for line in list(only_2)[:max_lines]:
                print(f"  + {line}")

    print(f"{'─'*80}")


def compare_csv_results(csv1, csv2):
    """
    Compare two CSV strings as sets of rows (order-independent).
    Ignores rows containing 'meta.fieldsites.se'.

    Args:
        csv1: First CSV string
        csv2: Second CSV string

    Returns:
        True if the CSV data rows match (ignoring order), False otherwise
    """
    try:
        # Parse both CSV strings
        reader1 = csv.reader(io.StringIO(csv1))
        reader2 = csv.reader(io.StringIO(csv2))

        # Convert to lists to get all rows
        rows1 = list(reader1)
        rows2 = list(reader2)

        # Handle empty results
        if len(rows1) == 0 and len(rows2) == 0:
            return True
        if len(rows1) == 0 or len(rows2) == 0:
            return False

        # Skip header row and convert data rows to sets of tuples
        # Strip whitespace from each cell
        # Filter out rows containing 'meta.fieldsites.se'
        data1 = set(
            tuple(cell.strip() for cell in row)
            for row in rows1[1:]  # Skip header
            if not any('meta.fieldsites.se' in cell for cell in row)
        )
        data2 = set(
            tuple(cell.strip() for cell in row)
            for row in rows2[1:]  # Skip header
            if not any('meta.fieldsites.se' in cell for cell in row)
        )

        return data1 == data2
    except Exception as e:
        # If CSV parsing fails, fall back to string comparison
        return csv1.strip() == csv2.strip()


def print_runtime_summary(query_runtimes):
    """
    Display runtime summary with top 10 longest queries and statistics.

    Args:
        query_runtimes: List of (index, elapsed_time) tuples
    """
    if not query_runtimes:
        print("\nNo queries were executed.")
        return

    print(f"\n{'='*80}")
    print("QUERY RUNTIME SUMMARY")
    print(f"{'='*80}")

    # Sort by elapsed time descending
    sorted_runtimes = sorted(query_runtimes, key=lambda x: x[1], reverse=True)

    # Show top 10 longest running queries
    top_n = min(10, len(sorted_runtimes))
    print(f"\nTop {top_n} Longest Running Queries:")
    for rank, (idx, elapsed) in enumerate(sorted_runtimes[:top_n], start=1):
        print(f"  {rank:2d}. Query #{idx}: {elapsed:.3f}s")

    # Calculate statistics
    min_runtime = min(query_runtimes, key=lambda x: x[1])
    max_runtime = max(query_runtimes, key=lambda x: x[1])

    print(f"\nRuntime Statistics:")
    print(f"  Min time: {min_runtime[1]:.3f}s (Query #{min_runtime[0]})")
    print(f"  Max time: {max_runtime[1]:.3f}s (Query #{max_runtime[0]})")
    print(f"{'='*80}")


def print_endpoint_comparison_summary(matched, mismatched, ep1_failures, ep2_failures, both_failed):
    """
    Display endpoint comparison summary.

    Args:
        matched: List of query IDs where both endpoints succeeded and matched
        mismatched: List of query IDs where both endpoints succeeded but results differ
        ep1_failures: List of query IDs that failed on endpoint 1 only
        ep2_failures: List of query IDs that failed on endpoint 2 only
        both_failed: List of query IDs that failed on both endpoints
    """
    print(f"\n{'='*80}")
    print("ENDPOINT COMPARISON SUMMARY")
    print(f"{'='*80}")
    print("Endpoint 1: http://localhost:65432/sparql (Accept: application/csv)")
    print("Endpoint 2: http://localhost:9094/sparql (Accept: text/csv)")
    print()

    # Results comparison
    print(f"Queries where both succeeded and matched: {len(matched)}")
    if matched:
        print(f"  Query IDs: {', '.join(map(str, matched))}")

    print(f"\nQueries where results mismatched: {len(mismatched)}")
    if mismatched:
        print(f"  Query IDs: {', '.join(map(str, mismatched))}")

    # Endpoint-specific failures
    print(f"\nEndpoint-specific failures:")
    print(f"  Failed on endpoint 1 only: {len(ep1_failures)}")
    if ep1_failures:
        print(f"    Query IDs: {', '.join(map(str, ep1_failures))}")

    print(f"  Failed on endpoint 2 only: {len(ep2_failures)}")
    if ep2_failures:
        print(f"    Query IDs: {', '.join(map(str, ep2_failures))}")

    print(f"  Failed on both endpoints: {len(both_failed)}")
    if both_failed:
        print(f"    Query IDs: {', '.join(map(str, both_failed))}")

    print(f"{'='*80}")


def main(input_file, output_file, skip_indexes=None, show_results=False):
    if skip_indexes is None:
        skip_indexes = set()

    print(f"Reading queries from: {input_file}")
    queries = extract_queries(input_file)

    print(f"\nFound {len(queries)} queries\n")

    if skip_indexes:
        print(f"Skipping queries: {', '.join(map(str, sorted(skip_indexes)))}\n")

    # Track failed queries, rewritten queries, and runtimes
    failed_queries = []
    rewritten_queries = []
    small_result_queries = []
    query_runtimes = []
    skipped_count = 0

    # Track endpoint comparison results
    endpoint1_failures = []  # Failed on endpoint 1 only
    endpoint2_failures = []  # Failed on endpoint 2 only
    both_endpoints_failed = []  # Failed on both
    mismatched_results = []  # Both succeeded but results differ
    matched_results = []  # Both succeeded and results match

    # Call print function on each query
    for idx, query in enumerate(queries, start=1):
        query = rewrite_distinct_keywords(query)

        # Check if this query should be skipped
        if idx in skip_indexes:
            print(f"\n{'='*80}")
            print(f"Query #{idx} - SKIPPED")
            print(f"{'='*80}")
            skipped_count += 1
            continue

        print_query(query, idx)

        rewritten_queries.append(f"Query #{idx}\n{'-'*80}\n{query}\n{'-'*80}\n")

        # Execute query on both endpoints
        endpoint1_host = 'http://localhost:65432/sparql'
        endpoint2_host = 'http://localhost:9094/sparql'

        success1, response1, error1, time1 = run_query(query, endpoint1_host, 'application/csv')
        success2, response2, error2, time2 = run_query(query, endpoint2_host, 'text/csv')

        # Track runtime for endpoint 1 (primary)
        query_runtimes.append((idx, time1))

        # Display results for both endpoints
        line_count1 = len(response1.splitlines()) if response1 else 0
        line_count2 = len(response2.splitlines()) if response2 else 0

        print(f"\nEndpoint 1 ({endpoint1_host}):")
        if success1:
            print(f"  Success ({time1:.3f}s, {line_count1} lines)")
        else:
            print(f"  Failed: {error1}")

        print(f"Endpoint 2 ({endpoint2_host}):")
        if success2:
            print(f"  Success ({time2:.3f}s, {line_count2} lines)")
        else:
            print(f"  Failed: {error2}")

        # Handle failures and compare results first
        # Determine if we have a mismatch before printing results
        has_mismatch = False
        if not success1 and not success2:
            # Both failed
            has_mismatch = True
            both_endpoints_failed.append(idx)
            failed_queries.append({
                'index': idx,
                'query': query,
                'response': f"EP1: {response1}\nEP2: {response2}",
                'error': f"EP1: {error1}, EP2: {error2}"
            })
        elif not success1:
            # Only endpoint 1 failed
            has_mismatch = True
            endpoint1_failures.append(idx)
            failed_queries.append({
                'index': idx,
                'query': query,
                'response': response1,
                'error': error1
            })
        elif not success2:
            # Only endpoint 2 failed
            has_mismatch = True
            endpoint2_failures.append(idx)
        else:
            # Both succeeded - compare results
            results_match = compare_csv_results(response1, response2)

            if results_match:
                matched_results.append(idx)
            else:
                has_mismatch = True
                mismatched_results.append(idx)

        # Print full results only on mismatch if requested
        if show_results and has_mismatch:
            if success1:
                print(f"\nResults from Endpoint 1:")
                print(f"{'─'*80}")
                # Limit output to 200 lines
                lines1 = response1.splitlines()
                if len(lines1) > 200:
                    print('\n'.join(lines1[:200]))
                    print(f"\n... ({len(lines1) - 200} more lines truncated)")
                else:
                    print(response1)
                print(f"{'─'*80}")

            if success2:
                print(f"\nResults from Endpoint 2:")
                print(f"{'─'*80}")
                # Limit output to 200 lines
                lines2 = response2.splitlines()
                if len(lines2) > 200:
                    print('\n'.join(lines2[:200]))
                    print(f"\n... ({len(lines2) - 200} more lines truncated)")
                else:
                    print(response2)
                print(f"{'─'*80}")

        # Print result status
        if not success1 and not success2:
            print(f"\nResult: ⚠️  BOTH ENDPOINTS FAILED")
        elif not success1:
            print(f"\nResult: ⚠️  ENDPOINT 1 FAILED")
        elif not success2:
            print(f"\nResult: ⚠️  ENDPOINT 2 FAILED")
        else:
            # Both succeeded
            if has_mismatch:
                print(f"\nResult: ✗ MISMATCH")
                # Show diff if requested
                if show_results:
                    print_diff(response1, response2)
            else:
                print(f"\nResult: ✓ MATCH")

            # Track queries with 1 or fewer rows (from endpoint 1)
            if line_count1 <= 2:
                small_result_queries.append(idx)

    # Save all rewritten queries to file
    with open(output_file, 'w') as f:
        f.write('\n'.join(rewritten_queries))
    print(f"\n✓ Saved {len(rewritten_queries)} rewritten queries to: {output_file}")

    # Save failed queries to file
    if failed_queries:
        failed_queries_file = 'failed_queries.txt'
        with open(failed_queries_file, 'w') as f:
            for failed in failed_queries:
                f.write(f"Query #{failed['index']}\n")
                f.write(f"{'='*80}\n")
                f.write(f"Error: {failed['error']}\n")
                f.write(f"{'-'*80}\n")
                f.write("Query:\n")
                f.write(f"{failed['query'].strip()}\n")
                f.write(f"{'-'*80}\n")
                f.write("Response:\n")
                f.write(f"{failed['response'] if failed['response'] else '(empty response)'}\n")
                f.write(f"{'='*80}\n\n")
        print(f"✓ Saved {len(failed_queries)} failed queries to: {failed_queries_file}")

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


    # Display endpoint comparison summary
    print_endpoint_comparison_summary(
        matched_results,
        mismatched_results,
        endpoint1_failures,
        endpoint2_failures,
        both_endpoints_failed
    )

    # Display runtime summary
    print_runtime_summary(query_runtimes)

    print(f"\n{'='*80}")
    print("EXECUTION SUMMARY")
    print(f"{'='*80}")
    print(f"Total queries: {len(queries)}")
    print(f"Executed: {len(queries) - skipped_count}")
    print(f"Skipped: {skipped_count}")
    print(f"Failed: {len(failed_queries)}")
    print(f"Queries returning ≤1 row: {len(small_result_queries)}")
    if small_result_queries:
        print(f"Query indices with ≤1 row: {', '.join(map(str, small_result_queries))}")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Run SPARQL queries and track their performance'
    )
    parser.add_argument(
        'input_file',
        nargs='?',
        default='../logs/manual_rewritten.log',
        help='Path to the file containing SPARQL queries (default: ../logs/manual_rewritten.log)'
    )
    parser.add_argument(
        '--output', '-o',
        default='rewritten_queries.txt',
        help='Output file for rewritten queries (default: rewritten_queries.txt)'
    )
    parser.add_argument(
        '--skip', '-s',
        default='',
        help='Comma-separated list of query indexes to skip (e.g., "1,3,5")'
    )
    parser.add_argument(
        '--show-results',
        action='store_true',
        help='Print full results from both endpoints and show diffs for mismatches'
    )

    args = parser.parse_args()

    # Parse skip list
    skip_indexes = set()
    if args.skip:
        try:
            skip_indexes = set(int(idx.strip()) for idx in args.skip.split(',') if idx.strip())
        except ValueError as e:
            print(f"Error: Invalid query index in skip list. Must be comma-separated integers.", file=sys.stderr)
            print(f"Example: --skip 1,3,5", file=sys.stderr)
            sys.exit(1)

    main(args.input_file, args.output, skip_indexes, args.show_results)
    print("DONE")
