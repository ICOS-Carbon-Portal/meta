#!/usr/bin/env python3
"""
Generate Ontop mappings (.obda file) from prefix_to_classes.json

This script reads the prefix_to_classes.json file and generates Ontop mappings
for all predicates found in the preds_subject arrays. Each predicate gets its own
mapping that queries the prefix-specific tables created by create_prefix_tables.py.
"""

import json
import re
import argparse
from pathlib import Path
from collections import defaultdict
from typing import Dict, Set, Tuple, Optional, List
import duckdb
import sys
sys.path.insert(0, "..")
from db_connection import get_connection


# Valid XSD datatypes - only these will be used for literal annotations
VALID_XSD_DATATYPES = {
    'http://www.w3.org/2001/XMLSchema#string',
    'http://www.w3.org/2001/XMLSchema#boolean',
    'http://www.w3.org/2001/XMLSchema#decimal',
    'http://www.w3.org/2001/XMLSchema#integer',
    'http://www.w3.org/2001/XMLSchema#double',
    'http://www.w3.org/2001/XMLSchema#float',
    'http://www.w3.org/2001/XMLSchema#date',
    'http://www.w3.org/2001/XMLSchema#time',
    'http://www.w3.org/2001/XMLSchema#dateTime',
    'http://www.w3.org/2001/XMLSchema#dateTimeStamp',
    'http://www.w3.org/2001/XMLSchema#gYear',
    'http://www.w3.org/2001/XMLSchema#gMonth',
    'http://www.w3.org/2001/XMLSchema#gDay',
    'http://www.w3.org/2001/XMLSchema#gYearMonth',
    'http://www.w3.org/2001/XMLSchema#gMonthDay',
    'http://www.w3.org/2001/XMLSchema#duration',
    'http://www.w3.org/2001/XMLSchema#yearMonthDuration',
    'http://www.w3.org/2001/XMLSchema#dayTimeDuration',
    'http://www.w3.org/2001/XMLSchema#byte',
    'http://www.w3.org/2001/XMLSchema#short',
    'http://www.w3.org/2001/XMLSchema#int',
    'http://www.w3.org/2001/XMLSchema#long',
    'http://www.w3.org/2001/XMLSchema#unsignedByte',
    'http://www.w3.org/2001/XMLSchema#unsignedShort',
    'http://www.w3.org/2001/XMLSchema#unsignedInt',
    'http://www.w3.org/2001/XMLSchema#unsignedLong',
    'http://www.w3.org/2001/XMLSchema#positiveInteger',
    'http://www.w3.org/2001/XMLSchema#nonNegativeInteger',
    'http://www.w3.org/2001/XMLSchema#negativeInteger',
    'http://www.w3.org/2001/XMLSchema#nonPositiveInteger',
    'http://www.w3.org/2001/XMLSchema#hexBinary',
    'http://www.w3.org/2001/XMLSchema#base64Binary',
    'http://www.w3.org/2001/XMLSchema#anyURI',
    'http://www.w3.org/2001/XMLSchema#language',
    'http://www.w3.org/2001/XMLSchema#normalizedString',
    'http://www.w3.org/2001/XMLSchema#token',
    'http://www.w3.org/2001/XMLSchema#NMTOKEN',
    'http://www.w3.org/2001/XMLSchema#Name',
    'http://www.w3.org/2001/XMLSchema#NCName',
}

# RDF type predicate - skip this in mapping generation
RDF_TYPE_URI = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'


def load_prefix_classes(json_path: str) -> dict:
    """Load and parse the prefix_to_classes.json file"""
    with open(json_path, 'r') as f:
        return json.load(f)


def extract_predicates_from_json(data: dict) -> Tuple[Set[str], List[Tuple[str, str]], int]:
    """
    Extract all unique predicates from preds_subject arrays and track their source prefixes

    Args:
        data: Parsed JSON data from prefix_to_classes.json

    Returns:
        Tuple of (set of predicate URIs, list of (predicate, prefix) pairs, count of skipped rdf:type)
    """
    predicates = set()
    predicate_prefix_pairs = []
    skipped_rdf_type_count = 0

    for prefix_uri, prefix_data in data.get('prefixes', {}).items():
        # Only process prefixes containing "icos-cp.eu"
        if 'icos-cp.eu' not in prefix_uri:
            continue

        preds_subject = prefix_data.get('preds_subject', [])
        for pred_info in preds_subject:
            pred_uri = pred_info.get('predicate_uri')
            if pred_uri:
                # Skip rdf:type predicate
                if pred_uri == RDF_TYPE_URI:
                    skipped_rdf_type_count += 1
                    continue

                predicates.add(pred_uri)
                # Store each (predicate, prefix) pair to generate mappings for all combinations
                predicate_prefix_pairs.append((pred_uri, prefix_uri))

    return predicates, predicate_prefix_pairs, skipped_rdf_type_count


def extract_prefixes(data: dict) -> List[str]:
    """
    Extract all prefix namespaces from the JSON

    Args:
        data: Parsed JSON data from prefix_to_classes.json

    Returns:
        List of prefix/namespace URIs (filtered to icos-cp.eu only)
    """
    prefixes = [p for p in data.get('prefixes', {}).keys() if 'icos-cp.eu' in p]
    return prefixes


def sanitize_table_name(prefix_uri: str) -> str:
    """
    Convert a prefix URI to a valid PostgreSQL table name
    (Copied from create_prefix_tables.py for consistency)

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
    (Copied from create_prefix_tables.py for consistency)

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


def extract_namespace(uri: str) -> str:
    """
    Extract namespace from URI (everything before last / or #)

    Example: 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName'
             -> 'http://meta.icos-cp.eu/ontologies/cpmeta/'
    """
    match = re.match(r'^(.*[/#])', uri)
    return match.group(1) if match else uri


def extract_local_name(uri: str) -> str:
    """
    Extract local name from URI (everything after last / or #)

    Example: 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' -> 'hasName'
    """
    match = re.search(r'[/#]([^/#]+)$', uri)
    return match.group(1) if match else uri


def generate_prefix_name(namespace: str) -> str:
    """
    Generate a short prefix name from a namespace URI

    Uses known prefixes first, then generates from URI structure
    """
    # Known standard prefixes
    known_prefixes = {
        'http://www.w3.org/1999/02/22-rdf-syntax-ns#': 'rdf',
        'http://www.w3.org/2000/01/rdf-schema#': 'rdfs',
        'http://www.w3.org/2002/07/owl#': 'owl',
        'http://www.w3.org/2001/XMLSchema#': 'xsd',
        'http://www.w3.org/ns/prov#': 'prov',
        'http://www.w3.org/2004/02/skos/core#': 'skos',
        'http://www.w3.org/ns/dcat#': 'dcat',
        'http://meta.icos-cp.eu/ontologies/cpmeta/': 'cpmeta',
        'http://meta.icos-cp.eu/ontologies/stationentry/': 'stationentry',
        'http://meta.icos-cp.eu/files/': 'files',
        'http://meta.icos-cp.eu/resources/': 'resources',
    }

    if namespace in known_prefixes:
        return known_prefixes[namespace]

    # Try to extract a meaningful prefix from the URI
    # Remove protocol
    without_protocol = re.sub(r'^https?://', '', namespace)

    # Try to get domain-based prefix
    parts = without_protocol.split('/')
    if len(parts) >= 2:
        # Use last meaningful part before the trailing slash
        for i in range(len(parts) - 1, -1, -1):
            if parts[i]:
                # Clean up the part
                prefix = re.sub(r'[^a-zA-Z0-9]', '', parts[i])
                if prefix:
                    prefix = prefix.lower()
                    # Ensure prefix starts with a letter (not a digit)
                    if prefix[0].isdigit():
                        prefix = f"ns{prefix}"
                    return prefix

    # Fallback: use hash of namespace
    return f"ns{abs(hash(namespace)) % 1000}"


def detect_prefixes(predicates: Set[str]) -> Dict[str, str]:
    """
    Auto-detect and generate prefixes from predicates

    Returns:
        Dict mapping namespace -> prefix name
    """
    namespaces = defaultdict(int)

    # Count namespace occurrences
    for pred in predicates:
        ns = extract_namespace(pred)
        namespaces[ns] += 1

    # Generate prefix for each namespace
    prefixes = {}
    used_prefix_names = set()

    for ns in sorted(namespaces.keys()):
        prefix_name = generate_prefix_name(ns)

        # Ensure uniqueness
        original_name = prefix_name
        counter = 2
        while prefix_name in used_prefix_names:
            prefix_name = f"{original_name}{counter}"
            counter += 1

        prefixes[ns] = prefix_name
        used_prefix_names.add(prefix_name)

    # Always include xsd for potential datatype ranges
    xsd_ns = 'http://www.w3.org/2001/XMLSchema#'
    if xsd_ns not in prefixes:
        prefixes[xsd_ns] = 'xsd'

    return prefixes


def shorten_uri(uri: str, prefixes: Dict[str, str]) -> str:
    """
    Shorten URI using prefix notation

    Example: 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' -> 'cpmeta:hasName'
    """
    ns = extract_namespace(uri)
    local = extract_local_name(uri)

    if ns in prefixes:
        return f"{prefixes[ns]}:{local}"
    else:
        return f"<{uri}>"


def sanitize_for_mapping_id(uri: str, prefixes: Dict[str, str]) -> str:
    """
    Convert URI to a valid mapping ID

    Example: 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' -> 'cpmeta_hasname'
    """
    ns = extract_namespace(uri)
    local = extract_local_name(uri)

    # Get prefix name
    prefix = prefixes.get(ns, 'unknown')

    # Clean local name: keep only alphanumeric and underscore
    clean_local = re.sub(r'[^a-zA-Z0-9_]', '_', local).lower()

    # Remove leading/trailing underscores
    clean_local = clean_local.strip('_')

    # Ensure doesn't start with digit
    if clean_local and clean_local[0].isdigit():
        clean_local = f"p_{clean_local}"

    return f"{prefix}_{clean_local}"


def check_if_uri_object(cursor, predicate: str, table_name: str, sample_size: int = 100) -> bool:
    """
    Heuristic to determine if a predicate's objects are URIs or literals

    Samples objects from prefix table and checks if they look like URIs
    """
    try:
        query = f"""
            SELECT object
            FROM {table_name}
            WHERE predicate = ?
            LIMIT {sample_size}
        """
        cursor.execute(query, (predicate,))
        results = cursor.fetchall()

        if not results:
            # Default to literal if no data
            return False

        # Count how many look like URIs (start with http:// or https://)
        uri_count = sum(1 for (obj,) in results
                       if obj and (obj.startswith('http://') or obj.startswith('https://')))

        # If more than 50% are URIs, treat as ObjectProperty
        return uri_count / len(results) > 0.5

    except Exception as e:
        print(f"Warning: Error checking object type for {predicate}: {e}")
        # Default to literal on error
        return False


def parse_ontology(ontology_path: Optional[str]) -> Dict[str, dict]:
    """
    Parse OWL ontology to determine property types

    Returns:
        Dict mapping predicate URI to {'is_object': bool, 'range': Optional[str]}
    """
    if not ontology_path or not Path(ontology_path).exists():
        return {}

    try:
        from rdflib import Graph, RDF, OWL, RDFS

        g = Graph()
        g.parse(ontology_path, format='turtle')

        property_info = {}

        # Find ObjectProperties
        for prop in g.subjects(RDF.type, OWL.ObjectProperty):
            prop_str = str(prop)
            range_uri = None
            for r in g.objects(prop, RDFS.range):
                range_uri = str(r)
                break
            property_info[prop_str] = {'is_object': True, 'range': range_uri}

        # Find DatatypeProperties
        for prop in g.subjects(RDF.type, OWL.DatatypeProperty):
            prop_str = str(prop)
            range_uri = None
            for r in g.objects(prop, RDFS.range):
                range_uri = str(r)
                break
            property_info[prop_str] = {'is_object': False, 'range': range_uri}

        return property_info

    except ImportError:
        print("Warning: rdflib not available, cannot parse ontology")
        return {}
    except Exception as e:
        print(f"Warning: Error parsing ontology: {e}")
        return {}


def get_property_info(predicate: str, table_name: str, ontology_info: Dict[str, dict],
                     cursor, use_db_heuristic: bool = True) -> Tuple[bool, Optional[str]]:
    """
    Get property information: is it an ObjectProperty and what's its range

    Returns:
        (is_object_property: bool, range_uri: Optional[str])
    """
    # Try ontology first
    if predicate in ontology_info:
        info = ontology_info[predicate]
        return info['is_object'], info.get('range')

    # Fall back to database heuristic
    if use_db_heuristic and cursor:
        is_uri = check_if_uri_object(cursor, predicate, table_name)
        return is_uri, None

    # Default to literal (DatatypeProperty) if unknown
    return False, None


def generate_mapping(predicate: str, prefix_uri: str, table_name: str,
                     prefixes: Dict[str, str], is_object_property: bool,
                     range_uri: Optional[str] = None) -> str:
    """
    Generate a single Ontop mapping for a predicate

    Args:
        predicate: Full predicate URI
        prefix_uri: The prefix URI for subjects (used in URI template)
        table_name: Name of the prefix table to query
        prefixes: Namespace to prefix mapping
        is_object_property: Whether objects are URIs (True) or literals (False)
        range_uri: Optional datatype/range URI for the object

    Returns:
        Formatted mapping string
    """
    # Ensure unique mapping ID by including table name (same predicate can appear in multiple tables)
    base_id = sanitize_for_mapping_id(predicate, prefixes)
    mapping_id = f"{table_name}_{base_id}"
    short_pred = shorten_uri(predicate, prefixes)

    # Format object based on property type
    if is_object_property:
        obj_format = "<{object}>"
    else:
        # Datatype property - always annotate with datatype
        # Validate that range_uri is a known XSD datatype
        if range_uri and range_uri in VALID_XSD_DATATYPES:
            short_range = shorten_uri(range_uri, prefixes)
            obj_format = f"{{object}}^^{short_range}"
        else:
            # Default to xsd:string for unknown/invalid datatypes
            obj_format = "{object}^^xsd:string"

    # Generate mapping using URI template for subject and prefix table query
    mapping = f"mappingId\t{mapping_id}\n"
    mapping += f"target\t\t<{prefix_uri}{{id}}> {short_pred} {obj_format} .\n"
    mapping += f"source\t\tSELECT id, object FROM {table_name} WHERE predicate = '{predicate}'\n"

    return mapping


def generate_obda_file(predicates: Set[str], predicate_prefix_pairs: List[Tuple[str, str]],
                       table_names: Dict[str, str], output_path: str,
                       ontology_path: Optional[str] = None,
                       db_connection_string: Optional[str] = None):
    """
    Generate complete .obda mapping file

    Args:
        predicates: Set of predicate URIs to generate mappings for
        predicate_prefix_pairs: List of (predicate_uri, prefix_uri) pairs
        table_names: Dict mapping prefix URI to table name
        output_path: Path to write the .obda file
        ontology_path: Optional path to OWL ontology file for property type info
        db_connection_string: Optional database connection for heuristics
    """
    # Parse ontology if provided
    ontology_info = parse_ontology(ontology_path)
    print(f"Loaded property info for {len(ontology_info)} predicates from ontology")

    # Connect to database if connection string provided
    cursor = None
    conn = None
    if db_connection_string:
        try:
            conn = psycopg2.connect(db_connection_string)
            cursor = conn.cursor()
            print("Connected to database for property type detection")
        except Exception as e:
            print(f"Warning: Could not connect to database: {e}")

    # Detect prefixes for namespace declarations
    prefixes = detect_prefixes(predicates)
    print(f"Detected {len(prefixes)} namespaces/prefixes")

    # Start building .obda file
    lines = []

    # Section 1: Prefix declarations
    lines.append("[PrefixDeclaration]")
    for ns, prefix in sorted(prefixes.items(), key=lambda x: x[1]):
        lines.append(f"{prefix}:\t{ns}")
    lines.append("")

    # Section 2: Mapping declaration header
    lines.append("[MappingDeclaration] @collection [[")
    lines.append("")

    # Section 3: Generate mappings for each predicate, grouped by prefix
    # Group (predicate, prefix) pairs by prefix
    prefix_to_preds = defaultdict(list)
    for predicate, prefix_uri in predicate_prefix_pairs:
        prefix_to_preds[prefix_uri].append(predicate)

    # Sort prefixes alphabetically
    sorted_prefix_uris = sorted(prefix_to_preds.keys())

    # Generate mappings grouped by prefix
    first_mapping = True
    mapping_count = 0
    for prefix_uri in sorted_prefix_uris:
        table_name = table_names.get(prefix_uri)
        if not table_name:
            print(f"Warning: No table name found for prefix {prefix_uri}, skipping")
            continue

        # Sort predicates within this prefix
        prefix_predicates = sorted(prefix_to_preds[prefix_uri])

        for predicate in prefix_predicates:
            # Add blank line before mapping (except before first one)
            if not first_mapping:
                lines.append("")
            first_mapping = False

            # Get property info
            is_object, range_uri = get_property_info(predicate, table_name, ontology_info, cursor)

            # Generate mapping
            mapping = generate_mapping(predicate, prefix_uri, table_name, prefixes, is_object, range_uri)
            lines.append(mapping)
            mapping_count += 1

    lines.append("")

    # Section 4: Close mapping declaration
    lines.append("]]")

    # Write to file
    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))

    # Clean up database connection
    if cursor:
        cursor.close()
    if conn:
        conn.close()

    print(f"Generated {mapping_count} mappings in {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description='Generate Ontop mappings from prefix_to_classes.json'
    )
    parser.add_argument(
        'json_file',
        nargs='?',
        default='scripts/prefix_to_classes.json',
        help='Path to prefix_to_classes.json file (default: scripts/prefix_to_classes.json)'
    )
    parser.add_argument(
        '-o', '--output',
        default='ontop/mapping/generated_from_prefixes.obda',
        help='Output .obda file path (default: ontop/mapping/generated_from_prefixes.obda)'
    )
    parser.add_argument(
        '--ontology',
        help='Path to OWL ontology file (.ttl) for property type detection'
    )
    parser.add_argument(
        '--db',
        help='Database connection string (e.g., "postgresql://user:pass@localhost/dbname")'
    )

    args = parser.parse_args()

    # Load JSON
    print(f"Loading {args.json_file}...")
    data = load_prefix_classes(args.json_file)

    # Extract predicates and their source prefixes
    predicates, predicate_prefix_pairs, skipped_rdf_type = extract_predicates_from_json(data)
    print(f"Found {len(predicates)} unique predicates in preds_subject arrays")
    print(f"Generating {len(predicate_prefix_pairs)} mappings (predicates may appear in multiple prefixes)")
    if skipped_rdf_type > 0:
        print(f"Skipped {skipped_rdf_type} rdf:type predicate(s)")

    if not predicates:
        print("No predicates found! Check your JSON file.")
        return

    # Extract all prefix URIs and generate table names
    all_prefixes = extract_prefixes(data)
    table_names = ensure_unique_table_names(all_prefixes)
    print(f"Generated table names for {len(table_names)} prefixes")

    # Generate mappings
    generate_obda_file(
        predicates,
        predicate_prefix_pairs,
        table_names,
        args.output,
        ontology_path=args.ontology,
        db_connection_string=args.db
    )

    print("Done!")


if __name__ == '__main__':
    main()
