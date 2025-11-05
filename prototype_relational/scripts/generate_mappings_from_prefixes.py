#!/usr/bin/env python3
"""
Generate Ontop mappings (.obda file) from prefix_to_classes.json

This script reads the prefix_to_classes.json file and generates Ontop mappings
for all predicates found in the preds_subject arrays. Each predicate gets its own
mapping that queries the rdf_triples table.
"""

import json
import re
import argparse
from pathlib import Path
from collections import defaultdict
from typing import Dict, Set, Tuple, Optional
import psycopg2


def load_prefix_classes(json_path: str) -> dict:
    """Load and parse the prefix_to_classes.json file"""
    with open(json_path, 'r') as f:
        return json.load(f)


def extract_predicates_from_json(data: dict) -> Set[str]:
    """
    Extract all unique predicates from preds_subject arrays

    Args:
        data: Parsed JSON data from prefix_to_classes.json

    Returns:
        Set of predicate URIs
    """
    predicates = set()

    for prefix, prefix_data in data.get('prefixes', {}).items():
        preds_subject = prefix_data.get('preds_subject', [])
        for pred_info in preds_subject:
            pred_uri = pred_info.get('predicate_uri')
            if pred_uri:
                predicates.add(pred_uri)

    return predicates


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
                    return prefix.lower()

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


def check_if_uri_object(cursor, predicate: str, sample_size: int = 100) -> bool:
    """
    Heuristic to determine if a predicate's objects are URIs or literals

    Samples objects from rdf_triples and checks if they look like URIs
    """
    try:
        query = f"""
            SELECT obj
            FROM rdf_triples
            WHERE pred = %s
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


def get_property_info(predicate: str, ontology_info: Dict[str, dict],
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
        is_uri = check_if_uri_object(cursor, predicate)
        return is_uri, None

    # Default to literal (DatatypeProperty) if unknown
    return False, None


def generate_mapping(predicate: str, prefixes: Dict[str, str],
                     is_object_property: bool, range_uri: Optional[str] = None) -> str:
    """
    Generate a single Ontop mapping for a predicate

    Args:
        predicate: Full predicate URI
        prefixes: Namespace to prefix mapping
        is_object_property: Whether objects are URIs (True) or literals (False)
        range_uri: Optional datatype/range URI for the object

    Returns:
        Formatted mapping string
    """
    mapping_id = sanitize_for_mapping_id(predicate, prefixes)
    short_pred = shorten_uri(predicate, prefixes)

    # Format object based on property type
    if is_object_property:
        obj_format = "<{obj}>"
    else:
        # Datatype property
        if range_uri:
            short_range = shorten_uri(range_uri, prefixes)
            obj_format = f"{{obj}}^^{short_range}"
        else:
            obj_format = "{obj}"

    # Generate mapping
    mapping = f"mappingId\t{mapping_id}\n"
    mapping += f"target\t\t<{{subj}}> {short_pred} {obj_format} .\n"
    mapping += f"source\t\tSELECT subj, obj FROM rdf_triples WHERE pred = '{predicate}'\n"

    return mapping


def generate_obda_file(predicates: Set[str], output_path: str,
                       ontology_path: Optional[str] = None,
                       db_connection_string: Optional[str] = None):
    """
    Generate complete .obda mapping file

    Args:
        predicates: Set of predicate URIs to generate mappings for
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

    # Detect prefixes
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

    # Section 3: Generate mappings for each predicate
    sorted_predicates = sorted(predicates)
    for i, predicate in enumerate(sorted_predicates):
        # Get property info
        is_object, range_uri = get_property_info(predicate, ontology_info, cursor)

        # Generate mapping
        mapping = generate_mapping(predicate, prefixes, is_object, range_uri)
        lines.append(mapping)

        # Add blank line between mappings (except after last one)
        if i < len(sorted_predicates) - 1:
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

    print(f"Generated {len(predicates)} mappings in {output_path}")


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

    # Extract predicates
    predicates = extract_predicates_from_json(data)
    print(f"Found {len(predicates)} unique predicates in preds_subject arrays")

    if not predicates:
        print("No predicates found! Check your JSON file.")
        return

    # Generate mappings
    generate_obda_file(
        predicates,
        args.output,
        ontology_path=args.ontology,
        db_connection_string=args.db
    )

    print("Done!")


if __name__ == '__main__':
    main()
