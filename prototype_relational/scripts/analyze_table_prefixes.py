#!/usr/bin/env python

import psycopg2
import json
from datetime import datetime
from collections import defaultdict


def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )


def load_reference_prefixes(filename='icos_subject_prefixes.json'):
    """Load the reference prefixes from icos_subject_prefixes.json."""
    with open(filename, 'r') as f:
        data = json.load(f)
    return list(data['prefix_counts'].keys())


def get_class_tables(conn):
    """Get all class table names (tables starting with ct_)."""
    cursor = conn.cursor()
    query = """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name LIKE 'ct_%'
        ORDER BY table_name;
    """
    cursor.execute(query)
    tables = [row[0] for row in cursor.fetchall()]
    cursor.close()
    return tables


def analyze_table_prefixes(conn, table_name, prefixes):
    """
    For a given table, find which prefixes are used in the id column.
    Returns a dictionary of {prefix: count}.
    """
    cursor = conn.cursor()
    prefix_counts = {}

    for prefix in prefixes:
        # Count how many IDs start with this prefix
        # Use parameterized query to avoid SQL injection
        query = f"""
            SELECT COUNT(*)
            FROM {table_name}
            WHERE id LIKE %s;
        """
        cursor.execute(query, (prefix + '%',))
        count = cursor.fetchone()[0]

        if count > 0:
            prefix_counts[prefix] = count

    cursor.close()
    return prefix_counts


def main():
    print("Analyzing table prefixes...")
    print("=" * 80)

    # Load reference prefixes
    print("Loading reference prefixes from icos_subject_prefixes.json...")
    prefixes = load_reference_prefixes()
    print(f"Loaded {len(prefixes)} reference prefixes")

    # Connect to database
    print("\nConnecting to PostgreSQL database...")
    conn = get_connection()

    # Get all class tables
    print("Fetching class tables...")
    tables = get_class_tables(conn)
    print(f"Found {len(tables)} class tables")

    # Analyze each table
    results = {}
    total_tables = len(tables)

    for i, table in enumerate(tables, 1):
        print(f"\n[{i}/{total_tables}] Analyzing {table}...", end=' ')
        prefix_counts = analyze_table_prefixes(conn, table, prefixes)
        results[table] = prefix_counts
        print(f"Found {len(prefix_counts)} prefixes used")

    # Close connection
    conn.close()

    # Prepare output data
    output_data = {
        'timestamp': datetime.now().isoformat(),
        'total_tables_analyzed': len(tables),
        'total_reference_prefixes': len(prefixes),
        'tables': results
    }

    # Write to file
    output_filename = 'table_prefix_analysis.json'
    with open(output_filename, 'w') as f:
        json.dump(output_data, f, indent=2)

    print("\n" + "=" * 80)
    print(f"Analysis complete! Results saved to {output_filename}")

    # Print summary
    print("\nSummary:")
    print(f"  Total tables analyzed: {len(tables)}")
    print(f"  Total reference prefixes: {len(prefixes)}")

    # Count how many tables use each prefix
    prefix_table_counts = defaultdict(int)
    for table, prefix_counts in results.items():
        for prefix in prefix_counts.keys():
            prefix_table_counts[prefix] += 1

    print(f"  Prefixes found in at least one table: {len(prefix_table_counts)}")

    # Show most common prefixes across tables
    if prefix_table_counts:
        print("\n  Top 10 most common prefixes (by number of tables):")
        sorted_prefixes = sorted(prefix_table_counts.items(),
                                key=lambda x: x[1], reverse=True)
        for prefix, count in sorted_prefixes[:10]:
            print(f"    {prefix}: {count} tables")


if __name__ == "__main__":
    main()
