#!/usr/bin/env python3
"""
Generate Ontop mappings for predicates with less than 100,000 instances.
These mappings query directly from the rdf_triples table rather than
predicate-specific tables, providing a general fallback for less common predicates.
"""

import duckdb
import argparse
import os
import sys
import re
import statistics
from collections import Counter

def get_predicate_counts():
    """Count entries for each unique predicate in the rdf_triples table."""
    conn = get_connection()
    cursor = conn.cursor()

    # Query to count entries per unique predicate
    query = """
        SELECT pred, COUNT(*) as count
        FROM rdf_triples
        GROUP BY pred
        ORDER BY count DESC;
    """

    cursor.execute(query)
    predicate_counts = cursor.fetchall()

    cursor.close()
    conn.close()

    return predicate_counts



    except psycopg2.Error as e:
        print(f"Error connecting to database: {e}")
        sys.exit(1)


def extract_namespace(uri):
    """
    Extract the namespace (base URI) from a full URI.
    Returns everything before the last / or #.
    """
    match = re.match(r'^(.*[/#])[^/#]+$', uri)
    if match:
        return match.group(1)
    return uri


def generate_prefix_name(namespace, existing_prefixes={}):
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
        'http://meta.icos-cp.eu/ontologies/otcmeta/': 'otcmeta',
        'http://meta.icos-cp.eu/resources/': 'cpres',
        'http://purl.org/dc/terms/': 'terms',
        'http://www.w3.org/ns/dcat#': 'dcat',
    }

    if namespace in known_prefixes:
        return known_prefixes[namespace]

    # Try to extract a meaningful name from the URI
    clean = namespace.replace('http://', '').replace('https://', '')
    clean = clean.rstrip('/#')

    # Try to extract the last meaningful component
    parts = [p for p in clean.split('/') if p]
    if parts:
        candidate = parts[-1]
        # Remove common suffixes
        candidate = re.sub(r'(ontology|ontologies|vocab|vocabulary)$', '', candidate)
        candidate = re.sub(r'[^a-z0-9]', '', candidate.lower())

        if candidate and not candidate[0].isdigit():
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
        Shortened URI (e.g., "cpmeta:hasName") or full URI in angle brackets
    """
    for namespace, prefix in prefixes.items():
        if uri.startswith(namespace):
            local_part = uri[len(namespace):]
            return f"{prefix}:{local_part}"

    # If no prefix found, return the full URI in angle brackets
    return f"<{uri}>"


def sanitize_mapping_id(predicate):
    """
    Sanitize predicate URI into a valid mapping ID.

    Args:
        predicate: URI string

    Returns:
        Sanitized ID like 'prefix_localname'
    """
    # Extract namespace and generate prefix
    namespace = extract_namespace(predicate)
    prefix = generate_prefix_name(namespace)

    # Extract the local name
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
    return f"{prefix}_{local_name}"


def check_if_uri_predicate(cursor, predicate, sample_size=100):
    """
    Check if a predicate's objects are primarily URIs or literals.

    Args:
        cursor: Database cursor
        predicate: Predicate URI
        sample_size: Number of rows to sample

    Returns:
        True if objects are primarily URIs, False if literals
    """
    try:
        cursor.execute("""
            SELECT obj FROM rdf_triples
            WHERE pred = ? AND obj IS NOT NULL
            LIMIT ?;
        """, (predicate, sample_size))

        rows = cursor.fetchall()
        if not rows:
            return False

        # Count how many look like URIs
        uri_count = 0
        for (obj_value,) in rows:
            if obj_value and (obj_value.startswith('http://') or obj_value.startswith('https://')):
                uri_count += 1

        # If more than 50% are URIs, treat as URI predicate
        return uri_count > len(rows) / 2

    except psycopg2.Error as e:
        print(f"Warning: Could not check predicate type for {predicate}: {e}")
        return False


def generate_mapping(predicate, prefixes, is_uri_object):
    """
    Generate a single Ontop mapping entry that queries from rdf_triples.

    Args:
        predicate: Full predicate URI
        prefixes: Dictionary of namespace -> prefix mappings
        is_uri_object: Whether the object should be wrapped in <>

    Returns:
        String containing the mapping entry
    """
    # Shorten the predicate using prefixes
    short_predicate = shorten_uri(predicate, prefixes)

    # Generate mapping ID
    mapping_id = sanitize_mapping_id(predicate)

    # Determine object format
    obj_format = "<{obj}>" if is_uri_object else "{obj}"

    # Generate the mapping - querying from rdf_triples with WHERE clause
    # Need to escape the predicate URI for the SQL query
    predicate_escaped = predicate.replace("'", "''")

    mapping = f"""mappingId\t{mapping_id}_general
target\t\t<{{subj}}> {short_predicate} {obj_format} .
source\t\tSELECT subj, obj FROM rdf_triples WHERE pred = '{predicate_escaped}'
"""

    return mapping


def generate_general_mappings(max_instances=100000, output_path='general_triples.obda'):
    """
    Generate Ontop mappings for predicates with fewer than max_instances.

    Args:
        max_instances: Maximum instance count threshold (default 100,000)
        output_path: Path to output .obda file
    """
    print(f"Generating mappings for predicates with < {max_instances:,} instances...")
    print()

    # Get predicate counts
    print("Fetching predicate counts from database...")
    predicate_counts = get_predicate_counts()

    if not predicate_counts:
        print("No predicates found in rdf_triples table.")
        return

    print(f"Found {len(predicate_counts)} unique predicates.")
    print()

    # Filter predicates with < max_instances and track skipped ones
    filtered_predicates = []
    skipped_predicates = []

    for pred, count in predicate_counts:
        if count < max_instances:
            filtered_predicates.append((pred, count))
        else:
            skipped_predicates.append((pred, count))

    print(f"Filtered to {len(filtered_predicates)} predicates with < {max_instances:,} instances.")
    print(f"Skipped {len(skipped_predicates)} predicates with >= {max_instances:,} instances.")

    if not filtered_predicates:
        print("No predicates meet the criteria.")
        return

    # Extract just the predicate URIs for prefix detection
    predicates = [pred for pred, _ in filtered_predicates]

    # Detect prefixes
    print()
    prefixes = detect_prefixes(predicates)

    # Ensure standard prefixes are included
    if 'http://www.w3.org/2001/XMLSchema#' not in prefixes:
        prefixes['http://www.w3.org/2001/XMLSchema#'] = 'xsd'
    if 'http://www.w3.org/2000/01/rdf-schema#' not in prefixes:
        prefixes['http://www.w3.org/2000/01/rdf-schema#'] = 'rdfs'

    # Connect to database to check object types
    conn = get_connection()
    cursor = conn.cursor()

    try:
        # Start building the output
        output_lines = []

        # Generate mappings for each predicate
        print("\nGenerating mappings...")
        for i, (predicate, count) in enumerate(filtered_predicates):
            # Check if objects are URIs or literals
            is_uri_obj = check_if_uri_predicate(cursor, predicate)

            # Generate mapping
            mapping = generate_mapping(predicate, prefixes, is_uri_obj)
            output_lines.append(mapping)

            if (i + 1) % 10 == 0:
                print(f"  Generated {i + 1}/{len(filtered_predicates)} mappings...")


        # Write to file
        print(f"\nWriting mappings to {output_path}...")
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(output_lines))

        # Write prefixes to separate file
        prefix_file = '../ontop/mapping/generated_prefixes.txt'
        print(f"Writing prefixes to {prefix_file}...")
        with open(prefix_file, 'w', encoding='utf-8') as f:
            for namespace, prefix in sorted(prefixes.items(), key=lambda x: x[1]):
                f.write(f"{prefix}: {namespace}\n")

        print(f"\n✓ Successfully generated {len(filtered_predicates)} mappings!")
        print(f"Output file: {output_path}")
        print(f"Prefixes file: {prefix_file}")

        # Print summary statistics
        total_instances = sum(count for _, count in filtered_predicates)
        instance_counts = [count for _, count in filtered_predicates]

        # Calculate min, max, and median
        min_instances = min(instance_counts) if instance_counts else 0
        max_instances_actual = max(instance_counts) if instance_counts else 0
        median_instances = statistics.median(instance_counts) if instance_counts else 0

        print(f"\nSummary:")
        print(f"  Total predicates: {len(filtered_predicates)}")
        print(f"  Total instances covered: {total_instances:,}")
        print(f"  Average instances per predicate: {total_instances // len(filtered_predicates):,}")
        print(f"  Lowest instances per predicate: {min_instances:,}")
        print(f"  Highest instances per predicate: {max_instances_actual:,}")
        print(f"  Median instances per predicate: {median_instances:,.1f}")

        # Print skipped predicates
        if skipped_predicates:
            print(f"\nSkipped predicates (>= {max_instances:,} instances):")
            print("-" * 100)
            print(f"{'Predicate':<80} {'Count':>15}")
            print("-" * 100)

            # Sort skipped predicates by count (descending)
            skipped_predicates_sorted = sorted(skipped_predicates, key=lambda x: x[1], reverse=True)

            for pred, count in skipped_predicates_sorted:
                # Truncate predicate if too long
                pred_display = pred if len(pred) <= 78 else pred[:75] + "..."
                print(f"{pred_display:<80} {count:>15,}")

            print("-" * 100)
            skipped_total = sum(count for _, count in skipped_predicates)
            print(f"{'TOTAL SKIPPED':<80} {skipped_total:>15,}")
            print(f"\nNote: These predicates were skipped because they exceed the threshold.")
            print(f"      Consider using predicate-specific tables for better performance.")

    except psycopg2.Error as e:
        print(f"\nDatabase error: {e}")
        sys.exit(1)
    except IOError as e:
        print(f"\nFile I/O error: {e}")
        sys.exit(1)
    finally:
        cursor.close()
        conn.close()


def main():
    """Main entry point with argument parsing."""
    parser = argparse.ArgumentParser(
        description="Generate Ontop mappings for predicates with fewer instances",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s                              # Default: < 100,000 instances
  %(prog)s --max-instances 50000        # Custom threshold
  %(prog)s -o custom_output.obda        # Custom output file
  %(prog)s --max-instances 10000 -o low_frequency.obda
        """
    )

    parser.add_argument(
        '--max-instances',
        type=int,
        default=100000,
        help='Maximum instance count threshold (default: 100,000)'
    )

    parser.add_argument(
        '-o', '--output',
        default='../ontop/mapping/generated_triples.obda',
        help='Output mappings file path (default: generated_triples.obda)'
    )

    args = parser.parse_args()

    # Generate mappings
    generate_general_mappings(args.max_instances, args.output)


if __name__ == "__main__":
    main()
