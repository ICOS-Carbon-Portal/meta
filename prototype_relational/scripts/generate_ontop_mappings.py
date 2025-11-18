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


from rdflib import Graph, Namespace, RDF, RDFS, OWL

def extract_namespace(uri):
    """
    Extract the namespace (base URI) from a full URI.
    Returns everything before the last / or #.
    """
    match = re.match(r'^(.*[/#])[^/#]+$', uri)
    if match:
        return match.group(1)
    return uri


def generate_prefix_name(namespace, existing_prefixes = {}):
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


def parse_ontology(ontology_path='../ontop/cpmeta.ttl'):
    """
    Parse the ontology file to determine property types and ranges.

    Args:
        ontology_path: Path to the TTL ontology file

    Returns:
        Dictionary mapping property URIs to dict with 'is_object' (bool) and 'range' (str or None)
        Example: {uri: {'is_object': False, 'range': 'http://www.w3.org/2001/XMLSchema#long'}}
    """
    property_info = {}

    if not os.path.exists(ontology_path):
        print(f"Warning: Ontology file not found at {ontology_path}. Using heuristics.")
        return property_info

    try:
        print(f"Parsing ontology from {ontology_path}...")
        g = Graph()
        g.parse(ontology_path, format='turtle')

        # Query for all ObjectProperty instances
        for prop in g.subjects(RDF.type, OWL.ObjectProperty):
            prop_uri = str(prop)
            # Get range if available
            range_uri = None
            for obj in g.objects(prop, RDFS.range):
                range_uri = str(obj)
                break  # Take first range

            property_info[prop_uri] = {
                'is_object': True,
                'range': range_uri
            }

        # Query for all DatatypeProperty instances
        for prop in g.subjects(RDF.type, OWL.DatatypeProperty):
            prop_uri = str(prop)
            # Get range if available
            range_uri = None
            for obj in g.objects(prop, RDFS.range):
                range_uri = str(obj)
                break  # Take first range

            property_info[prop_uri] = {
                'is_object': False,
                'range': range_uri
            }

        obj_count = sum(1 for v in property_info.values() if v['is_object'])
        data_count = sum(1 for v in property_info.values() if not v['is_object'])

        print(f"  Found {obj_count} ObjectProperties")
        print(f"  Found {data_count} DatatypeProperties")

        return property_info

    except Exception as e:
        print(f"Warning: Error parsing ontology: {e}")
        print("Falling back to heuristics.")
        return {}


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


def get_property_info(predicate, property_info, cursor, table_name):
    """
    Get property information including whether it's an object property and its range.
    Uses ontology first, falls back to heuristics if not found.

    Args:
        predicate: Full predicate URI
        property_info: Dictionary from parse_ontology()
        cursor: Database cursor (for fallback heuristic)
        table_name: Table name (for fallback heuristic)

    Returns:
        Tuple of (is_object: bool, range_uri: str or None)
    """
    # First try ontology lookup
    if predicate in property_info:
        info = property_info[predicate]
        return info['is_object'], info.get('range')

    # Fall back to heuristic if not found in ontology
    print(f"  Note: {predicate} not found in ontology, using heuristic")
    is_object = check_if_uri_column(cursor, table_name)
    return is_object, None


def generate_mapping(predicate, table_name, prefixes, is_uri_object, range_uri=None):
    """
    Generate a single Ontop mapping entry.

    Args:
        predicate: Full predicate URI
        table_name: Sanitized table name (already includes prefix from sanitize_predicate)
        prefixes: Dictionary of namespace -> prefix mappings
        is_uri_object: Whether the object should be wrapped in <>
        range_uri: Optional datatype range URI (e.g., 'http://www.w3.org/2001/XMLSchema#long')

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

        # Add datatype suffix if range is specified and it's a datatype property
        # Only add for valid URIs (not blank nodes)
        if range_uri and (range_uri.startswith('http://') or range_uri.startswith('https://')):
            short_range = shorten_uri(range_uri, prefixes)
            # Only add the type if it's not just a generic rdfs:Literal
            if short_range and 'Literal' not in short_range:
                obj_format = f"{{obj}}^^{short_range}"

    # Generate the mapping
    mapping = f"""mappingId\t{mapping_id}
target\t\t<{{subj}}> {short_predicate} {obj_format} .
source\t\tSELECT subj, obj FROM {table_name}
"""

    return mapping


def generate_mappings_file(conn, output_path, ontology_path='../ontop/cpmeta.ttl'):
    """
    Generate the complete Ontop mappings file.

    Args:
        conn: Database connection
        output_path: Path to output .obda file
        ontology_path: Path to ontology TTL file
    """
    cursor = conn.cursor()

    try:
        # Parse ontology to determine property types and ranges
        property_info = parse_ontology(ontology_path)
        print()

        # Load table name to predicate URI mappings
        print("Loading predicate table mappings...")
        try:
            cursor.execute("SELECT table_name, predicate_uri FROM predicate_table_mappings;")
            table_to_predicate = dict(cursor.fetchall())
            print(f"  Loaded {len(table_to_predicate)} table mappings")
        except psycopg2.Error as e:
            print(f"  Warning: Could not load predicate_table_mappings: {e}")
            print("  Will use direct predicate URIs instead")
            table_to_predicate = {}

        print()

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

        # Ensure standard prefixes are included (for datatype ranges)
        if 'http://www.w3.org/2001/XMLSchema#' not in prefixes:
            prefixes['http://www.w3.org/2001/XMLSchema#'] = 'xsd'
        if 'http://www.w3.org/2000/01/rdf-schema#' not in prefixes:
            prefixes['http://www.w3.org/2000/01/rdf-schema#'] = 'rdfs'

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

            # Look up the original predicate URI from the mapping table
            # This ensures we use the exact URI that matches the ontology
            original_predicate = table_to_predicate.get(table_name, predicate)

            # Get property info (type and range) from ontology, or use heuristics
            is_uri_obj, range_uri = get_property_info(original_predicate, property_info, cursor, table_name)

            # Generate mapping (use original predicate for the target)
            mapping = generate_mapping(original_predicate, table_name, prefixes, is_uri_obj, range_uri)
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
  %(prog)s -o mappings.obda --ontology custom_ontology.ttl
  DB_HOST=localhost %(prog)s -o mappings.obda

Note:
  Requires rdflib library: pip install rdflib
  Falls back to heuristics if rdflib is not installed or ontology file not found.
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
        default='../ontop/generated_mappings.obda',
        help='Output mappings file path (default: generated_mappings.obda)'
    )

    # Ontology file argument
    parser.add_argument(
        '--ontology',
        default='../ontop/cpmeta.ttl',
        help='Path to ontology TTL file (default: ontop/cpmeta.ttl)'
    )

    args = parser.parse_args()

    # Display configuration
    print(f"Connecting to PostgreSQL at {args.host}:{args.port} as {args.user}...")

    # Connect to database
    conn = get_connection()

    try:
        # Generate mappings
        generate_mappings_file(conn, args.output, args.ontology)

    finally:
        conn.close()


if __name__ == "__main__":
    main()
