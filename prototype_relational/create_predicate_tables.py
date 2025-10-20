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


def extract_namespace(uri):
    """
    Extract the namespace (base URI) from a full URI.
    Returns everything before the last / or #.
    """
    match = re.match(r'^(.*[/#])[^/#]+$', uri)
    if match:
        return match.group(1)
    return uri


def generate_prefix_name(namespace):
    """
    Generate a short prefix name for a namespace.
    Uses common conventions or extracts from the URI.
    """
    # Common known prefixes
    known_prefixes = {
        'http://www.w3.org/1999/02/22-rdf-syntax-ns#': 'rdf',
        'http://www.w3.org/2000/01/rdf-schema#': 'rdfs',
        'http://www.w3.org/2002/07/owl#': 'owl',
        'http://www.w3.org/ns/prov#': 'prov',
        'http://www.w3.org/2001/XMLSchema#': 'xsd',
        'http://purl.org/dc/elements/1.1/': 'dc',
        'http://purl.org/dc/terms/': 'dcterms',
        'http://xmlns.com/foaf/0.1/': 'foaf',
        'http://meta.icos-cp.eu/ontologies/cpmeta/': 'cpmeta',
        'https://meta.icos-cp.eu/objects/': 'metaobjects',
        'http://meta.icos-cp.eu/resources/': 'cpres',
    }

    if namespace in known_prefixes:
        return known_prefixes[namespace]

    # Try to extract a meaningful name from the URI
    # Remove protocol and common patterns
    clean = namespace.replace('http://', '').replace('https://', '')
    clean = clean.rstrip('/#')

    # Try to extract the last meaningful component
    parts = [p for p in clean.split('/') if p]
    if parts:
        # Use the last part or a combination
        candidate = parts[-1]
        # Remove common suffixes
        candidate = re.sub(r'(ontology|ontologies|vocab|vocabulary)$', '', candidate)
        candidate = re.sub(r'[^a-z0-9]', '', candidate.lower())

        if candidate:
            return candidate

    # Fall back to a hash-based prefix
    # Use a simple hash of the namespace to ensure uniqueness
    import hashlib
    hash_val = hashlib.md5(namespace.encode()).hexdigest()[:6]
    return f"ns{hash_val}"


def get_connection():
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
    Includes the namespace prefix to prevent collisions.

    Args:
        predicate: URI string like 'http://example.com/ontology/hasName'

    Returns:
        Sanitized table name like 'prefix_hasname'
    """
    # Extract namespace and generate prefix
    namespace = extract_namespace(predicate)
    prefix = generate_prefix_name(namespace)

    # Extract the last component (local name) after the last / or #
    match = re.search(r'[/#]([^/#]+)$', predicate)
    if match:
        local_name = match.group(1)
    else:
        local_name = predicate

    # Replace any non-alphanumeric characters with underscores
    local_name = re.sub(r'[^a-zA-Z0-9_]', '_', local_name)

    # Ensure local name doesn't start with a number
    if local_name and local_name[0].isdigit():
        local_name = f"pred_{local_name}"

    # Convert to lowercase for consistency
    local_name = local_name.lower()

    # Combine prefix and local name
    table_name = f"{prefix}_{local_name}"

    return table_name


def clear_predicate_tables(cursor):
    """
    Drop all predicate tables and the mapping table.

    Args:
        cursor: Database cursor

    Returns:
        Number of tables dropped
    """
    print("Clearing all predicate tables...")

    # Check if mapping table exists
    cursor.execute("""
        SELECT EXISTS (
            SELECT FROM information_schema.tables
            WHERE table_name = 'predicate_table_mappings'
        );
    """)

    mapping_table_exists = cursor.fetchone()[0]

    if not mapping_table_exists:
        print("No predicate_table_mappings table found. Nothing to clear.")
        return 0

    # Get all table names from the mapping table
    cursor.execute("SELECT table_name FROM predicate_table_mappings ORDER BY table_name;")
    table_names = [row[0] for row in cursor.fetchall()]

    if not table_names:
        print("No predicate tables found in mapping table.")
        # Still drop the empty mapping table
        cursor.execute("DROP TABLE IF EXISTS predicate_table_mappings CASCADE;")
        print("Dropped predicate_table_mappings table.")
        return 1

    print(f"Found {len(table_names)} predicate tables to drop...")

    # Drop each predicate table
    dropped_count = 0
    for table_name in table_names:
        try:
            cursor.execute(f"DROP TABLE IF EXISTS {table_name} CASCADE;")
            dropped_count += 1
            if dropped_count % 10 == 0:
                print(f"  Dropped {dropped_count}/{len(table_names)} tables...")
        except psycopg2.Error as e:
            print(f"  Warning: Could not drop table {table_name}: {e}")

    # Drop the mapping table
    cursor.execute("DROP TABLE IF EXISTS predicate_table_mappings CASCADE;")
    dropped_count += 1

    print(f"✓ Cleared {dropped_count} tables (including predicate_table_mappings)")
    return dropped_count


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
            if predicate == 'pred':
                continue

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
  %(prog)s                        # Create tables from all predicates
  %(prog)s --limit 1000           # Create tables with max 1000 rows per table
  %(prog)s --clear                # Clear existing tables only (no recreation)
  %(prog)s --clear --limit 1000   # Clear then recreate tables with limit
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

    # Clear argument
    parser.add_argument(
        '--clear',
        action='store_true',
        help='Clear all existing predicate tables and mapping table (when used alone, only clears; use with --limit to clear and recreate)'
    )

    args = parser.parse_args()

    # Display configuration
    print(f"Connecting to PostgreSQL at {args.host}:{args.port} as {args.user}...")
    if args.limit:
        print(f"Limiting to {args.limit} triples per predicate table")

    # Connect to database
    conn = get_connection()

    try:
        cursor = conn.cursor()

        # Determine if we should create tables
        # If only --clear is given (no --limit), just clear and exit
        should_create = not (args.clear and args.limit is None)

        # Clear existing tables if requested
        if args.clear:
            clear_predicate_tables(cursor)
            conn.commit()

            if not should_create:
                print("\n✓ Cleared all predicate tables. No new tables created.")
                cursor.close()
                return

            print()

        # Process all predicates to create and populate tables
        process_predicates(conn, args.limit)

        cursor.close()

        # Show how to view created tables
        print("\nTo view all created tables, run:")
        print(f"  psql --host {args.host} --port {args.port} -U {args.user} -d {args.dbname} -c \"\\dt\"")
        print("\nTo view predicate mappings, run:")
        print(f"  psql --host {args.host} --port {args.port} -U {args.user} -d {args.dbname} -c \"SELECT * FROM predicate_table_mappings;\"")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
