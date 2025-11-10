#!/usr/bin/env python3
"""
Generate and create database tables from prefix_to_classes.json

This script reads the prefix_to_classes.json file and creates one table per prefix.
Each table has columns: id, predicate, object
The id column contains the subject URI with the prefix stripped.
"""

import json
import re
import argparse
from pathlib import Path
from collections import defaultdict
from typing import Dict, Set, List
import psycopg2


def load_prefix_classes(json_path: str) -> dict:
    """Load and parse the prefix_to_classes.json file"""
    with open(json_path, 'r') as f:
        return json.load(f)


def extract_prefixes(data: dict) -> List[str]:
    """
    Extract all prefix namespaces from the JSON

    Args:
        data: Parsed JSON data from prefix_to_classes.json

    Returns:
        List of prefix/namespace URIs
    """
    prefixes = list(data.get('prefixes', {}).keys())
    return prefixes


def sanitize_table_name(prefix_uri: str) -> str:
    """
    Convert a prefix URI to a valid PostgreSQL table name

    Example: 'http://meta.icos-cp.eu/resources/' -> 'resources'
             'http://meta.icos-cp.eu/ontologies/cpmeta/' -> 'cpmeta'
    """
    # Remove protocol
    without_protocol = re.sub(r'^https?://', '', prefix_uri)

    # Remove trailing slash/hash
    without_protocol = without_protocol.rstrip('/#')

    # Split by slashes
    parts = without_protocol.split('/')

    # Try to extract meaningful name from the last non-empty parts
    meaningful_parts = [p for p in parts if p and p not in ['www', 'com', 'org', 'eu']]

    if meaningful_parts:
        # Use last 1-2 parts for more specificity
        if len(meaningful_parts) >= 2:
            # For paths like "meta.icos-cp.eu/resources/acq_"
            # or "meta.icos-cp.eu/ontologies/cpmeta"
            name_parts = meaningful_parts[-2:]
            table_name = '_'.join(name_parts)
        else:
            table_name = meaningful_parts[-1]
    else:
        # Fallback: use hash
        table_name = f"prefix_{abs(hash(prefix_uri)) % 10000}"

    # Clean up: keep only alphanumeric and underscore
    table_name = re.sub(r'[^a-zA-Z0-9_]', '_', table_name)

    # Remove consecutive underscores
    table_name = re.sub(r'_+', '_', table_name)

    # Remove leading/trailing underscores
    table_name = table_name.strip('_')

    # Ensure doesn't start with digit
    if table_name and table_name[0].isdigit():
        table_name = f"t_{table_name}"

    # Convert to lowercase
    table_name = table_name.lower()

    # Ensure it's not a reserved word
    reserved_words = {'user', 'table', 'select', 'insert', 'update', 'delete', 'from', 'where'}
    if table_name in reserved_words:
        table_name = f"tbl_{table_name}"

    return table_name


def ensure_unique_table_names(prefixes: List[str]) -> Dict[str, str]:
    """
    Generate unique table names for all prefixes

    Returns:
        Dict mapping prefix URI -> unique table name
    """
    table_names = {}
    used_names = set()

    for prefix in sorted(prefixes):
        base_name = sanitize_table_name(prefix)
        table_name = base_name

        # Handle collisions
        counter = 2
        while table_name in used_names:
            table_name = f"{base_name}{counter}"
            counter += 1

        table_names[prefix] = table_name
        used_names.add(table_name)

    return table_names


def generate_create_table_sql(table_name: str) -> str:
    """
    Generate CREATE TABLE statement

    Args:
        table_name: Name of the table to create

    Returns:
        SQL statement
    """
    sql = f"""-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS {table_name} (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);
"""
    return sql


def generate_insert_sql(table_name: str, prefix: str) -> str:
    """
    Generate INSERT statement to populate table from rdf_triples

    The id column is populated by stripping the prefix from the subject URI.

    Args:
        table_name: Name of the table to insert into
        prefix: The prefix URI to filter subjects

    Returns:
        SQL statement
    """
    # Calculate prefix length
    prefix_len = len(prefix)

    # Use SUBSTRING to strip the prefix from subject
    sql = f"""-- Populate table with data from rdf_triples
INSERT INTO {table_name} (id, predicate, object)
SELECT
    SUBSTRING(subj FROM {prefix_len + 1}) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE '{prefix}%';
"""
    return sql


def generate_index_sql(table_name: str) -> str:
    """
    Generate index creation statements for performance

    Args:
        table_name: Name of the table to create indexes on

    Returns:
        SQL statements
    """
    sql = f"""-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_{table_name}_id ON {table_name}(id);
CREATE INDEX IF NOT EXISTS idx_{table_name}_predicate ON {table_name}(predicate);
"""
    return sql


def generate_all_sql(prefixes: List[str], table_names: Dict[str, str],
                     include_drop: bool = False) -> str:
    """
    Generate complete SQL script for all prefixes

    Args:
        prefixes: List of prefix URIs
        table_names: Mapping of prefix URI to table name
        include_drop: Whether to include DROP TABLE statements

    Returns:
        Complete SQL script as string
    """
    lines = []

    # Header
    lines.append("-- Generated SQL for prefix-based tables")
    lines.append("-- Each table contains subjects with a specific prefix")
    lines.append("-- Structure: (id, predicate, object)")
    lines.append("--   id: subject URI with prefix removed")
    lines.append("--   predicate: full predicate URI")
    lines.append("--   object: object value (URI or literal)")
    lines.append("")

    # Generate SQL for each prefix
    for prefix in sorted(prefixes):
        table_name = table_names[prefix]

        lines.append("-- " + "=" * 70)
        lines.append(f"-- Prefix: {prefix}")
        lines.append(f"-- Table: {table_name}")
        lines.append("-- " + "=" * 70)
        lines.append("")

        # Drop table if requested
        if include_drop:
            lines.append(f"DROP TABLE IF EXISTS {table_name} CASCADE;")
            lines.append("")

        # Create table
        lines.append(generate_create_table_sql(table_name))
        lines.append("")

        # Insert data
        lines.append(generate_insert_sql(table_name, prefix))
        lines.append("")

        # Create indexes
        lines.append(generate_index_sql(table_name))
        lines.append("")

    # Summary
    lines.append("-- " + "=" * 70)
    lines.append(f"-- Summary: Created {len(prefixes)} tables")
    lines.append("-- " + "=" * 70)

    return '\n'.join(lines)


def execute_sql(sql: str, connection_string: str):
    """
    Execute SQL statements in the database

    Args:
        sql: SQL script to execute
        connection_string: PostgreSQL connection string
    """
    print("Connecting to database...")
    conn = psycopg2.connect(connection_string)

    try:
        cursor = conn.cursor()

        # Split into individual statements and execute
        # Note: This is a simple approach; for complex SQL use proper parsing
        statements = [s.strip() for s in sql.split(';') if s.strip() and not s.strip().startswith('--')]

        print(f"Executing {len(statements)} SQL statements...")
        for i, statement in enumerate(statements, 1):
            if statement:
                print(f"  [{i}/{len(statements)}] Executing...", end='\r')
                cursor.execute(statement)

        print(f"\nCommitting changes...")
        conn.commit()
        print("Done!")

    except Exception as e:
        print(f"\nError executing SQL: {e}")
        conn.rollback()
        raise

    finally:
        cursor.close()
        conn.close()


def print_summary(prefixes: List[str], table_names: Dict[str, str], data: dict):
    """
    Print summary of tables to be created

    Args:
        prefixes: List of prefix URIs
        table_names: Mapping of prefix URI to table name
        data: The full JSON data with counts
    """
    print("\n" + "=" * 80)
    print("TABLE GENERATION SUMMARY")
    print("=" * 80)
    print(f"Total prefixes: {len(prefixes)}")
    print(f"Total tables to create: {len(table_names)}")
    print("\nTables:")
    print("-" * 80)

    prefix_data_map = data.get('prefixes', {})

    for prefix in sorted(prefixes):
        table_name = table_names[prefix]
        count = prefix_data_map.get(prefix, {}).get('total_count', 0)
        print(f"  {table_name:30} <- {prefix:40} ({count:6} rows)")

    print("=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description='Generate database tables from prefix_to_classes.json'
    )
    parser.add_argument(
        'json_file',
        nargs='?',
        default='scripts/prefix_to_classes.json',
        help='Path to prefix_to_classes.json file (default: scripts/prefix_to_classes.json)'
    )
    parser.add_argument(
        '-o', '--output',
        default='scripts/create_prefix_tables.sql',
        help='Output SQL file path (default: scripts/create_prefix_tables.sql)'
    )
    parser.add_argument(
        '--execute',
        action='store_true',
        help='Execute SQL directly in database (requires --db)'
    )
    parser.add_argument(
        '--db',
        help='Database connection string (e.g., "postgresql://user:pass@localhost/dbname")'
    )
    parser.add_argument(
        '--drop',
        action='store_true',
        help='Include DROP TABLE statements (WARNING: will delete existing tables)'
    )

    args = parser.parse_args()

    # Validate arguments
    if args.execute and not args.db:
        parser.error("--execute requires --db connection string")

    # Load JSON
    print(f"Loading {args.json_file}...")
    data = load_prefix_classes(args.json_file)

    # Extract prefixes
    prefixes = extract_prefixes(data)
    print(f"Found {len(prefixes)} prefixes")

    if not prefixes:
        print("No prefixes found! Check your JSON file.")
        return

    # Generate unique table names
    table_names = ensure_unique_table_names(prefixes)

    # Print summary
    print_summary(prefixes, table_names, data)

    # Generate SQL
    print(f"\nGenerating SQL...")
    sql = generate_all_sql(prefixes, table_names, include_drop=args.drop)

    # Write to file
    print(f"Writing SQL to {args.output}...")
    with open(args.output, 'w') as f:
        f.write(sql)
    print(f"SQL written to {args.output}")

    # Execute if requested
    if args.execute:
        print("\n" + "=" * 80)
        print("EXECUTING SQL IN DATABASE")
        print("=" * 80)
        if args.drop:
            response = input("WARNING: --drop specified. This will DELETE existing tables. Continue? (yes/no): ")
            if response.lower() != 'yes':
                print("Aborted.")
                return

        try:
            execute_sql(sql, args.db)
            print("\nAll tables created and populated successfully!")
        except Exception as e:
            print(f"\nFailed to execute SQL: {e}")
            return

    print("\nDone!")


if __name__ == '__main__':
    main()
