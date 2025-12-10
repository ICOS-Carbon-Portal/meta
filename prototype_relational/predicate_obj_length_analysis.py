#!/usr/bin/python

import duckdb
from db_connection import get_connection
from collections import defaultdict
import json


def get_predicate_length_stats():
    """Get min, max, and avg length of obj field for each predicate from rdf_triples."""
    conn = get_connection()
    cursor = conn.cursor()

    query = """
        SELECT
            pred,
            COUNT(*) as count,
            MIN(LENGTH(obj)) as min_length,
            MAX(LENGTH(obj)) as max_length,
            AVG(LENGTH(obj))::INTEGER as avg_length
        FROM rdf_triples
        WHERE obj IS NOT NULL
        GROUP BY pred
        ORDER BY pred;
    """

    cursor.execute(query)
    results = cursor.fetchall()

    cursor.close()
    conn.close()

    # Convert to list of dictionaries
    return [
        {
            'predicate': pred,
            'count': count,
            'min_length': min_len,
            'max_length': max_len,
            'avg_length': avg_len
        }
        for pred, count, min_len, max_len, avg_len in results
    ]


def group_by_namespace(predicate_stats):
    """Group predicate statistics by their namespace."""
    namespaces = defaultdict(list)

    for stat in predicate_stats:
        pred = stat['predicate']

        # Extract namespace (everything before the last # or /)
        if '#' in pred:
            namespace = pred.rsplit('#', 1)[0] + '#'
            local_name = pred.rsplit('#', 1)[1]
        elif '/' in pred:
            namespace = pred.rsplit('/', 1)[0] + '/'
            local_name = pred.rsplit('/', 1)[1]
        else:
            namespace = "no-namespace"
            local_name = pred

        namespaces[namespace].append({
            'full_uri': pred,
            'local_name': local_name,
            'count': stat['count'],
            'min_length': stat['min_length'],
            'max_length': stat['max_length'],
            'avg_length': stat['avg_length']
        })

    return namespaces


def write_json_output(predicate_stats, namespaces, filename="predicate_obj_lengths.json"):
    """Write the results to a JSON file."""
    output = {
        'summary': {
            'total_predicates': len(predicate_stats),
            'total_triples': sum(stat['count'] for stat in predicate_stats),
            'overall_min_length': min(stat['min_length'] for stat in predicate_stats) if predicate_stats else 0,
            'overall_max_length': max(stat['max_length'] for stat in predicate_stats) if predicate_stats else 0,
        },
        'by_namespace': {}
    }

    # Add namespace-grouped data
    for ns in sorted(namespaces.keys()):
        output['by_namespace'][ns] = namespaces[ns]

    # Write to file
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"\nJSON output written to: {filename}")


def main():
    print("Querying rdf_triples table for predicate obj length statistics...")
    print()

    # Get predicate length statistics from database
    predicate_stats = get_predicate_length_stats()

    # Group by namespace
    namespaces = group_by_namespace(predicate_stats)

    # Calculate overall statistics
    total_predicates = len(predicate_stats)
    total_triples = sum(stat['count'] for stat in predicate_stats)
    overall_min = min(stat['min_length'] for stat in predicate_stats) if predicate_stats else 0
    overall_max = max(stat['max_length'] for stat in predicate_stats) if predicate_stats else 0

    # Display summary
    print("=" * 140)
    print("SUMMARY")
    print("=" * 140)
    print(f"Total unique predicates:                 {total_predicates}")
    print(f"Total entries in rdf_triples:            {total_triples:,}")
    print(f"Overall min obj length:                  {overall_min}")
    print(f"Overall max obj length:                  {overall_max:,}")
    print()
    print("=" * 140)

    # Display results grouped by namespace
    print()
    print("PREDICATE OBJ LENGTH STATISTICS BY NAMESPACE")
    print("=" * 140)

    for ns in sorted(namespaces.keys()):
        print()
        print(f"Namespace: {ns}")
        print("-" * 140)
        print(f"{'Predicate':<50} | {'Count':>12} | {'Min Len':>8} | {'Max Len':>10} | {'Avg Len':>8}")
        print("-" * 140)

        for stat in namespaces[ns]:
            print(f"{stat['local_name']:<50} | {stat['count']:>12,} | {stat['min_length']:>8} | {stat['max_length']:>10,} | {stat['avg_length']:>8}")

    print()
    print("=" * 140)

    # Top predicates by max length
    print()
    print("TOP 20 PREDICATES BY MAX OBJ LENGTH")
    print("=" * 140)
    print(f"{'Predicate (Full IRI)':<100} | {'Count':>12} | {'Max Len':>10}")
    print("-" * 140)

    sorted_by_max = sorted(predicate_stats, key=lambda x: x['max_length'], reverse=True)[:20]
    for stat in sorted_by_max:
        pred = stat['predicate']
        print(f"{pred:<100} | {stat['count']:>12,} | {stat['max_length']:>10,}")

    print()
    print("=" * 140)

    # Write JSON output
    write_json_output(predicate_stats, namespaces)


if __name__ == "__main__":
    try:
        main()
    except psycopg2.OperationalError as e:
        print(f"Error: Could not connect to the database.")
        print(f"Details: {e}")
        print("Please ensure PostgreSQL is running and credentials are correct.")
    except Exception as e:
        print(f"Error: {e}")
        raise
