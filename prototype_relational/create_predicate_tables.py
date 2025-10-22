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

try:
    from rdflib import Graph, RDF, RDFS, OWL
    HAS_RDFLIB = True
except ImportError:
    HAS_RDFLIB = False


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
            # If starts with number, find a meaningful word from other parts
            if candidate[0].isdigit():
                prefix_word = None
                # Look through other parts (backwards) to find a non-numeric word
                for part in reversed(parts[:-1]):  # Skip the last part (candidate)
                    clean_part = re.sub(r'[^a-z0-9]', '', part.lower())
                    # Check if it's a meaningful word (has letters and doesn't start with digit)
                    if clean_part and not clean_part[0].isdigit() and len(clean_part) > 1:
                        prefix_word = clean_part
                        break

                # Fall back to 'ns' if no meaningful word found
                if not prefix_word:
                    prefix_word = "ns"

                candidate = f"{prefix_word}{candidate}"

            return candidate

    # Fall back to a hash-based prefix
    # Use a simple hash of the namespace to ensure uniqueness
    import hashlib
    hash_val = hashlib.md5(namespace.encode()).hexdigest()[:6]
    return f"ns{hash_val}"


def parse_ontology_for_types(ontology_path='ontop/cpmeta.ttl'):
    """
    Parse the ontology file to determine property ranges for type mapping.

    Args:
        ontology_path: Path to the TTL ontology file

    Returns:
        Dictionary mapping property URIs to their range URIs
    """
    property_ranges = {}

    if not HAS_RDFLIB:
        return property_ranges

    if not os.path.exists(ontology_path):
        return property_ranges

    try:
        g = Graph()
        g.parse(ontology_path, format='turtle')

        # Get ranges for both ObjectProperty and DatatypeProperty
        for prop in g.subjects(RDF.type, OWL.ObjectProperty):
            prop_uri = str(prop)
            for obj in g.objects(prop, RDFS.range):
                range_uri = str(obj)
                if range_uri.startswith('http://') or range_uri.startswith('https://'):
                    property_ranges[prop_uri] = range_uri
                break

        for prop in g.subjects(RDF.type, OWL.DatatypeProperty):
            prop_uri = str(prop)
            for obj in g.objects(prop, RDFS.range):
                range_uri = str(obj)
                if range_uri.startswith('http://') or range_uri.startswith('https://'):
                    property_ranges[prop_uri] = range_uri
                break

        return property_ranges

    except Exception:
        return {}


def xsd_to_postgres_type(range_uri):
    """
    Map XSD datatype URIs to PostgreSQL column types.

    Args:
        range_uri: XSD datatype URI (e.g., 'http://www.w3.org/2001/XMLSchema#long')

    Returns:
        PostgreSQL type string (e.g., 'BIGINT')
    """
    if not range_uri:
        return 'TEXT'

    # Extract the local part after the #
    if '#' in range_uri:
        xsd_type = range_uri.split('#')[-1]
    else:
        return 'TEXT'

    # Map XSD types to PostgreSQL types
    type_mapping = {
        'long': 'BIGINT',
        'integer': 'INTEGER',
        'int': 'INTEGER',
        'short': 'SMALLINT',
        'byte': 'SMALLINT',
        'float': 'REAL',
        'double': 'DOUBLE PRECISION',
        'decimal': 'NUMERIC',
        'boolean': 'BOOLEAN',
        'date': 'DATE',
        'dateTime': 'TIMESTAMP',
        'time': 'TIME',
        'string': 'TEXT',
        'anyURI': 'TEXT',
    }

    return type_mapping.get(xsd_type, 'TEXT')


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


def create_predicate_table(cursor, table_name, typ):
    """Create a predicate table with subj and obj columns."""
    # Drop existing table
    cursor.execute(f"DROP TABLE IF EXISTS {table_name} CASCADE;")

    # Create new table
    cursor.execute(f"""
        CREATE TABLE {table_name} (
            subj TEXT NOT NULL,
            obj {typ} NOT NULL
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
            WHERE obj IS NOT NULL AND pred = %s
            LIMIT %s;
        """, (predicate, limit))
    else:
        cursor.execute(f"""
            INSERT INTO {table_name} (subj, obj)
            SELECT subj, obj FROM rdf_triples
            WHERE obj IS NOT NULL AND pred = %s;
        """, (predicate,))

    return cursor.rowcount


def create_index(cursor, table_name, with_obj):
    """
    Create an index on the subj column for faster subject lookups.

    Args:
        cursor: Database cursor
        table_name: Name of the predicate table
    """
    index_name = f"idx_{table_name}_subj"
    cursor.execute(f"CREATE INDEX {index_name} ON {table_name}(subj);")
    if with_obj:
        index_name = f"idx_{table_name}_obj"
        cursor.execute(f"CREATE INDEX {index_name} ON {table_name}(obj);")


def process_predicates(conn, limit=None, add_index=False):
    """
    Main processing function to create and populate all predicate tables.

    Args:
        conn: Database connection
        limit: Optional limit on triples per predicate
        add_index: Whether to create indexes on subj column
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
        index_count = 0
        for predicate in predicates:
            if predicate == 'pred':
                continue

            # Sanitize the predicate to create a valid table name
            table_name = sanitize_predicate(predicate)
            if 'rdf-syntax-ns' in predicate :
                print(f'Skipping rdf-syntax predicate: {predicate}')
                continue

            print(f"\nProcessing predicate: {predicate}")
            print(f"  -> Table name: {table_name}")

            otypes = parse_ontology_for_types()
            # print(otypes.keys())
            # print(otypes.get(predicate))
            pg_type = xsd_to_postgres_type(otypes.get(predicate))
            print("type", pg_type)
            # Create the table
            print("  -> Creating table...")
            create_predicate_table(cursor, table_name, "TEXT")

            # Populate the table
            print("  -> Populating table...")
            row_count = populate_predicate_table(cursor, table_name, predicate, limit)

            print(f"  -> Inserted {row_count} rows")
            total_rows += row_count

            # Create index if requested
            if add_index:
                print("  -> Creating index on subj...")
                create_index(cursor, table_name, table_name not in ['cpmeta_asgeojson', 'dcterms_description'])
                index_count += 1

            # Insert mapping
            insert_mapping(cursor, table_name, predicate)
            mapping_count += 1

            # Commit after each predicate to avoid holding locks
            conn.commit()

        print(f"\n✓ Successfully created and populated all predicate tables!")
        print(f"Total rows inserted: {total_rows}")
        print(f"Total mappings created: {mapping_count}")
        if add_index:
            print(f"Total indexes created: {index_count}")

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
  %(prog)s --index                # Create tables with indexes on subj column
  %(prog)s --index --limit 1000   # Create tables with indexes and row limit
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

    # Index argument
    parser.add_argument(
        '--index',
        action='store_true',
        help='Create indexes on subj column for each predicate table for faster subject lookups'
    )

    args = parser.parse_args()

    # Display configuration
    print(f"Connecting to PostgreSQL at {args.host}:{args.port} as {args.user}...")
    if args.limit:
        print(f"Limiting to {args.limit} triples per predicate table")
    if args.index:
        print("Creating indexes on subj column for each table")

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
        process_predicates(conn, args.limit, args.index)

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
