#!/usr/bin/env python3
"""
Script to generate Ontop mappings file from rdf_triples predicates.
Creates one mapping per predicate using the predicate tables.
"""

import psycopg2
import argparse
import os
import sys
import re
from collections import Counter
from urllib.parse import urlparse
from create_predicate_tables import sanitize_predicate, get_connection

def extract_namespace(uri):
    """
    Extract the namespace (base URI) from a full URI.
    Returns everything before the last / or #.
    """
    match = re.match(r'^(.*[/#])[^/#]+$', uri)
    if match:
        return match.group(1)
    return uri


def generate_prefix_name(namespace, existing_prefixes):
    """
    Generate a short prefix name for a namespace.
    Tries to use common conventions or extracts from the URI.
    """
    # Common known prefixes
    known_prefixes = {
        'http://www.w3.org/1999/02/22-rdf-syntax-ns#': 'rdf',
        'http://www.w3.org/2000/01/rdf-schema#': 'rdfs',
        'http://www.w3.org/2002/07/owl#': 'owl',
        'http://www.w3.org/ns/prov#': 'prov',
        'http://www.w3.org/2001/XMLSchema#': 'xsd',
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

            if candidate not in existing_prefixes.values():
                return candidate

    # Fall back to generating a sequential prefix
    for i in range(1, 1000):
        prefix = f"ns{i}"
        if prefix not in existing_prefixes.values():
            return prefix

    return "ns"


def detect_prefixes(predicates, min_count=2):
    """
    Auto-detect common prefixes from predicates.

    Args:
        predicates: List of predicate URIs
        min_count: Minimum number of predicates to warrant a prefix

    Returns:
        Dictionary mapping namespace URIs to prefix names
    """
    print("Auto-detecting prefixes from predicates...")

    # Count namespace occurrences
    namespace_counts = Counter()
    for pred in predicates:
        namespace = extract_namespace(pred)
        if namespace != pred:  # Only count if we extracted a namespace
            namespace_counts[namespace] += 1

    # Generate prefixes for common namespaces
    prefixes = {}
    for namespace, count in namespace_counts.most_common():
        if count >= min_count:
            prefix_name = generate_prefix_name(namespace, prefixes)
            prefixes[namespace] = prefix_name

    print(f"Detected {len(prefixes)} prefixes:")
    for namespace, prefix in prefixes.items():
        print(f"  {prefix}: {namespace}")

    return prefixes


def shorten_uri(uri, prefixes):
    """
    Shorten a URI using detected prefixes.

    Args:
        uri: Full URI to shorten
        prefixes: Dictionary of namespace -> prefix mappings

    Returns:
        Shortened URI (e.g., "cpmeta:hasName") or original if no prefix found
    """
    for namespace, prefix in prefixes.items():
        if uri.startswith(namespace):
            local_part = uri[len(namespace):]
            return f"{prefix}:{local_part}"

    # If no prefix found, return the full URI in angle brackets
    return f"<{uri}>"


def check_if_uri_column(cursor, table_name, sample_size=100):
    """
    Check if the 'obj' column in a table contains primarily URIs or literals.

    Args:
        cursor: Database cursor
        table_name: Name of the predicate table
        sample_size: Number of rows to sample

    Returns:
        True if objects are primarily URIs, False if literals
    """
    try:
        cursor.execute(f"""
            SELECT obj FROM {table_name}
            WHERE obj IS NOT NULL
            LIMIT {sample_size};
        """)

        rows = cursor.fetchall()
        if not rows:
            return False

        # Count how many look like URIs
        uri_count = 0
        for (obj_value,) in rows:
            if obj_value and (obj_value.startswith('http://') or obj_value.startswith('https://')):
                uri_count += 1

        # If more than 50% are URIs, treat as URI column
        return uri_count > len(rows) / 2

    except psycopg2.Error as e:
        print(f"Warning: Could not check column type for {table_name}: {e}")
        return False


def generate_mapping(predicate, table_name, prefixes, is_uri_object):
    """
    Generate a single Ontop mapping entry.

    Args:
        predicate: Full predicate URI
        table_name: Sanitized table name (already includes prefix from sanitize_predicate)
        prefixes: Dictionary of namespace -> prefix mappings
        is_uri_object: Whether the object should be wrapped in <>

    Returns:
        String containing the mapping entry
    """
    # Shorten the predicate using prefixes
    short_predicate = shorten_uri(predicate, prefixes)

    # Use table name as mapping ID (it already includes the prefix from sanitize_predicate)
    mapping_id = table_name

    # Determine object format
    if is_uri_object:
        obj_format = "<{obj}>"
    else:
        obj_format = "{obj}"

    # Generate the mapping
    mapping = f"""mappingId\t{mapping_id}
target\t\t<{{subj}}> {short_predicate} {obj_format} .
source\t\tSELECT subj, obj FROM {table_name}
"""

    return mapping


def generate_mappings_file(conn, output_path):
    """
    Generate the complete Ontop mappings file.

    Args:
        conn: Database connection
        output_path: Path to output .obda file
    """
    cursor = conn.cursor()

    try:
        print("Fetching predicates from rdf_triples...")
        cursor.execute("SELECT DISTINCT pred FROM rdf_triples ORDER BY pred;")
        predicates = [row[0] for row in cursor.fetchall()]

        if not predicates:
            print("No predicates found in rdf_triples table.")
            return

        print(f"Found {len(predicates)} unique predicates.")

        # Filter out the 'pred' predicate
        predicates = [p for p in predicates if p != 'pred']
        if len(predicates) == 0:
            print("No valid predicates found after filtering.")
            return

        # Detect prefixes
        prefixes = detect_prefixes(predicates)

        # Start building the output
        output_lines = []

        # Write prefix declarations
        output_lines.append("[PrefixDeclaration]")
        for namespace, prefix in sorted(prefixes.items(), key=lambda x: x[1]):
            # Format namespace with proper ending
            if namespace.endswith(('#', '/')):
                ns_format = namespace
            else:
                ns_format = namespace

            output_lines.append(f"{prefix}: {ns_format}")

        output_lines.append("")
        output_lines.append("[MappingDeclaration] @collection [[")
        output_lines.append("")

        # Generate mappings for each predicate
        print("\nGenerating mappings...")
        for i, predicate in enumerate(predicates):
            table_name = sanitize_predicate(predicate)

            # Check if objects are URIs or literals
            is_uri_object = check_if_uri_column(cursor, table_name)

            # Generate mapping
            mapping = generate_mapping(predicate, table_name, prefixes, is_uri_object)
            output_lines.append(mapping)

            if (i + 1) % 10 == 0:
                print(f"  Generated {i + 1}/{len(predicates)} mappings...")

        # Close the mapping declaration
        output_lines.append("]]")
        output_lines.append("")

        # Write to file
        print(f"\nWriting mappings to {output_path}...")
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(output_lines))

        print(f"✓ Successfully generated {len(predicates)} mappings!")
        print(f"Output file: {output_path}")

    except psycopg2.Error as e:
        print(f"\nDatabase error: {e}")
        sys.exit(1)
    except IOError as e:
        print(f"\nFile I/O error: {e}")
        sys.exit(1)
    finally:
        cursor.close()


def main():
    """Main entry point with argument parsing."""
    parser = argparse.ArgumentParser(
        description="Generate Ontop mappings file from rdf_triples predicates",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Environment variables:
  DB_HOST         Database host (default: localhost)
  DB_PORT         Database port (default: 5432)
  DB_USER         Database user (default: postgres)
  DB_NAME         Database name (default: postgres)
  DB_PASSWORD     Database password (optional)

Examples:
  %(prog)s -o mappings.obda
  %(prog)s --host db --output generated_mappings.obda
  DB_HOST=localhost %(prog)s -o mappings.obda
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

    parser.add_argument(
        '--password',
        default=os.environ.get('DB_PASSWORD'),
        help='Database password (default: $DB_PASSWORD)'
    )

    # Output file argument
    parser.add_argument(
        '-o', '--output',
        default='ontop/generated_mappings.obda',
        help='Output mappings file path (default: generated_mappings.obda)'
    )

    args = parser.parse_args()

    # Display configuration
    print(f"Connecting to PostgreSQL at {args.host}:{args.port} as {args.user}...")

    # Connect to database
    conn = get_connection()

    try:
        # Generate mappings
        generate_mappings_file(conn, args.output)

    finally:
        conn.close()


if __name__ == "__main__":
    main()
