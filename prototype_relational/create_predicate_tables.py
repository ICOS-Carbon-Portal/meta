#!/usr/bin/env python3
"""
Script to create and populate predicate-based tables from rdf_triples.
Each unique predicate gets its own table with subj and obj columns.
"""

import psycopg2
import argparse
import os
import sys
import re


def get_connection(host, port, user, dbname):
    """Create and return a PostgreSQL database connection."""
    try:
        return psycopg2.connect(
            host="localhost",
            user="postgres",
            port=5432,
            password="ontop"
        )
    except psycopg2.Error as e:
        print(f"Error connecting to database: {e}")
        sys.exit(1)


def sanitize_predicate(predicate):
    """
    Sanitize predicate URIs into valid PostgreSQL table names.

    Args:
        predicate: URI string like 'http://example.com/ontology/hasName'

    Returns:
        Sanitized table name like 'hasname'
    """
    # Extract the last component after the last / or #
    match = re.search(r'[/#]([^/#]+)$', predicate)
    if match:
        name = match.group(1)
    else:
        name = predicate

    # Replace any non-alphanumeric characters with underscores
    name = re.sub(r'[^a-zA-Z0-9_]', '_', name)

    # Ensure it doesn't start with a number
    if name and name[0].isdigit():
        name = f"pred_{name}"

    # Convert to lowercase for consistency
    name = name.lower()

    return name


def fetch_predicates(cursor):
    """Fetch all unique predicates from rdf_triples table."""
    print("Fetching all unique predicates from rdf_triples...")

    cursor.execute("SELECT DISTINCT pred FROM rdf_triples ORDER BY pred;")
    predicates = [row[0] for row in cursor.fetchall()]

    if not predicates:
        print("No predicates found in rdf_triples table.")
        return []

    print(f"Found {len(predicates)} unique predicates.")
    return predicates


def create_mapping_table(cursor):
    """Create a table to map sanitized table names to original predicate URIs."""
    print("Creating predicate_table_mappings table...")

    # Drop existing table
    cursor.execute("DROP TABLE IF EXISTS predicate_table_mappings CASCADE;")

    # Create new mapping table
    cursor.execute("""
        CREATE TABLE predicate_table_mappings (
            table_name TEXT PRIMARY KEY,
            predicate_uri TEXT NOT NULL
        );
    """)


def insert_mapping(cursor, table_name, predicate_uri):
    """
    Insert a mapping between table name and predicate URI.

    Args:
        cursor: Database cursor
        table_name: Sanitized table name
        predicate_uri: Original predicate URI
    """
    cursor.execute("""
        INSERT INTO predicate_table_mappings (table_name, predicate_uri)
        VALUES (%s, %s)
        ON CONFLICT (table_name) DO UPDATE
        SET predicate_uri = EXCLUDED.predicate_uri;
    """, (table_name, predicate_uri))


def create_predicate_table(cursor, table_name):
    """Create a predicate table with subj and obj columns."""
    # Drop existing table
    cursor.execute(f"DROP TABLE IF EXISTS {table_name} CASCADE;")

    # Create new table
    cursor.execute(f"""
        CREATE TABLE {table_name} (
            subj TEXT NOT NULL,
            obj TEXT
        );
    """)


def populate_predicate_table(cursor, table_name, predicate, limit=None):
    """
    Populate a predicate table from rdf_triples.

    Args:
        cursor: Database cursor
        table_name: Name of the table to populate
        predicate: Predicate URI to filter by
        limit: Optional limit on number of rows to insert

    Returns:
        Number of rows inserted
    """
    if limit:
        cursor.execute(f"""
            INSERT INTO {table_name} (subj, obj)
            SELECT subj, obj FROM rdf_triples
            WHERE pred = %s
            LIMIT %s;
        """, (predicate, limit))
    else:
        cursor.execute(f"""
            INSERT INTO {table_name} (subj, obj)
            SELECT subj, obj FROM rdf_triples
            WHERE pred = %s;
        """, (predicate,))

    return cursor.rowcount


def process_predicates(conn, limit=None):
    """
    Main processing function to create and populate all predicate tables.

    Args:
        conn: Database connection
        limit: Optional limit on triples per predicate
    """
    cursor = conn.cursor()

    try:
        # Fetch all unique predicates
        predicates = fetch_predicates(cursor)

        if not predicates:
            return

        # Create the mapping table before processing predicates
        create_mapping_table(cursor)
        conn.commit()

        # Process each predicate
        total_rows = 0
        mapping_count = 0
        for predicate in predicates:
            # Sanitize the predicate to create a valid table name
            table_name = sanitize_predicate(predicate)

            print(f"\nProcessing predicate: {predicate}")
            print(f"  -> Table name: {table_name}")

            # Create the table
            print("  -> Creating table...")
            create_predicate_table(cursor, table_name)

            # Populate the table
            print("  -> Populating table...")
            row_count = populate_predicate_table(cursor, table_name, predicate, limit)

            print(f"  -> Inserted {row_count} rows")
            total_rows += row_count

            # Insert mapping
            insert_mapping(cursor, table_name, predicate)
            mapping_count += 1

            # Commit after each predicate to avoid holding locks
            conn.commit()

        print(f"\n✓ Successfully created and populated all predicate tables!")
        print(f"Total rows inserted: {total_rows}")
        print(f"Total mappings created: {mapping_count}")

    except psycopg2.Error as e:
        print(f"\nError processing predicates: {e}")
        conn.rollback()
        sys.exit(1)
    except Exception as e:
        print(f"\nUnexpected error: {e}")
        conn.rollback()
        sys.exit(1)
    finally:
        cursor.close()


def main():
    """Main entry point with argument parsing."""
    parser = argparse.ArgumentParser(
        description="Create and populate predicate-based tables from rdf_triples",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Environment variables:
  DB_HOST         Database host (default: localhost)
  DB_PORT         Database port (default: 5432)
  DB_USER         Database user (default: postgres)
  DB_NAME         Database name (default: postgres)
  LIMIT           Limit triples per predicate (optional)

Examples:
  %(prog)s --limit 1000
  LIMIT=500 DB_HOST=myhost %(prog)s
  %(prog)s --host db --port 5433 --limit 10000
        """
    )

    # Database connection arguments
    parser.add_argument(
        '--host',
        default=os.environ.get('DB_HOST', 'localhost'),
        help='Database host (default: localhost or $DB_HOST)'
    )

    parser.add_argument(
        '--port',
        type=int,
        default=int(os.environ.get('DB_PORT', '5432')),
        help='Database port (default: 5432 or $DB_PORT)'
    )

    parser.add_argument(
        '--user',
        default=os.environ.get('DB_USER', 'postgres'),
        help='Database user (default: postgres or $DB_USER)'
    )

    parser.add_argument(
        '--dbname',
        default=os.environ.get('DB_NAME', 'postgres'),
        help='Database name (default: postgres or $DB_NAME)'
    )

    # Limit argument
    limit_env = os.environ.get('LIMIT')
    parser.add_argument(
        '--limit',
        type=int,
        default=int(limit_env) if limit_env else None,
        metavar='N',
        help='Limit the number of triples inserted per predicate table'
    )

    args = parser.parse_args()

    # Display configuration
    print(f"Connecting to PostgreSQL at {args.host}:{args.port} as {args.user}...")
    if args.limit:
        print(f"Limiting to {args.limit} triples per predicate table")

    # Connect to database
    conn = get_connection(args.host, args.port, args.user, args.dbname)

    try:
        # Process all predicates
        process_predicates(conn, args.limit)

        # Show how to view created tables
        print("\nTo view all created tables, run:")
        print(f"  psql --host {args.host} --port {args.port} -U {args.user} -d {args.dbname} -c \"\\dt\"")
        print("\nTo view predicate mappings, run:")
        print(f"  psql --host {args.host} --port {args.port} -U {args.user} -d {args.dbname} -c \"SELECT * FROM predicate_table_mappings;\"")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
