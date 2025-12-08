#!/usr/bin/env python3
"""
Generate Ontop mappings for all SQL class tables from class predicates analysis.

This script reads:
- class_predicates_analysis.json: Class and predicate metadata
- table_prefix_analysis.json: Table to prefix mappings
- class_tables/create_foreign_keys.sql: Foreign key relationships
- predicate_types.json: SQL types for proper literal handling
- predicate_cardinality.json: Cardinality data for array detection
- ../ontop/cpmeta.ttl: Ontology for functional property detection

Outputs:
- ../ontop/mapping/generated_all_mappings.obda: Scalar (non-array) property mappings
- ../ontop/mapping/generated_array_mappings.obda: Array property mappings
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from collections import defaultdict
from rdflib import Graph, RDF, RDFS, OWL
from generate_class_tables import (
    MERGE_GROUPS,
    detect_functional_properties,
    load_cardinality_analysis,
    get_predicate_columns
)

def sanitize_table_name(class_name: str) -> str:
    """
    Convert class name to table name format (same as generate_class_tables.py).

    Example: 'cpmeta:DataObject' -> 'ct_data_objects'
             'prov:Activity' -> 'ct_prov_activities'
             'MERGED:ct_object_specs' -> 'ct_object_specs'
    """
    # Handle merged classes - they already have the table name
    if class_name.startswith('MERGED:'):
        return class_name.replace('MERGED:', '')

    # Remove namespace prefix
    if ':' in class_name:
        namespace, name = class_name.split(':', 1)
    else:
        # Handle full URIs
        name = class_name.split('/')[-1].split('#')[-1]
        namespace = ''

    # Convert CamelCase to snake_case
    name = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', name)
    name = name.lower()

    # Pluralize (simple heuristic)
    if not name.endswith('s'):
        if name.endswith('y') and len(name) > 2 and name[-2] not in 'aeiou':
            name = name[:-1] + 'ies'
        elif name.endswith(('s', 'x', 'z', 'ch', 'sh')):
            name = name + 'es'
        else:
            name = name + 's'

    # Add namespace prefix if not cpmeta (to avoid collisions)
    if namespace and namespace != 'cpmeta':
        name = f"{namespace}_{name}"

    # Clean up: keep only alphanumeric and underscore
    name = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    name = re.sub(r'_+', '_', name)
    name = name.strip('_')

    # Ensure doesn't start with digit
    if name and name[0].isdigit():
        name = f"t_{name}"

    # Ensure it's not a reserved word
    reserved_words = {'user', 'table', 'select', 'insert', 'update', 'delete', 'from', 'where', 'group', 'order'}
    if name in reserved_words:
        name = f"tbl_{name}"

    # Add ct_ prefix to all table names
    return f"ct_{name}"


def get_basic_lens_name(table_name: str) -> str:
    """
    Convert table name to BasicLens name by removing ct_ prefix.

    Args:
        table_name: Table name (e.g., 'ct_collections')

    Returns:
        Lens name without ct_ prefix (e.g., 'collections')
    """
    if table_name.startswith('ct_'):
        return table_name[3:]  # Remove 'ct_' prefix
    return table_name


def create_lens_names(column_name: str) -> Tuple[str, str]:
    """
    Convert an array column name to lens name parts.

    Args:
        column_name: Array column name (e.g., 'has_part', 'has_column')

    Returns:
        Tuple of (lens_suffix, new_column_name)
        e.g., 'has_column' -> ('columns', 'column')
    """
    base = column_name

    # Remove common prefixes
    for prefix in ['has_', 'is_', 'was_', 'had_', 'were_', 'been_']:
        if base.startswith(prefix):
            base = base[len(prefix):]
            break

    # Remove _of suffix (for cases like 'is_next_version_of')
    if base.endswith('_of'):
        base = base[:-3]

    # Pluralize for lens name (add 's' if doesn't end in 's')
    lens_suffix = base + 's' if not base.endswith('s') else base

    # Singularize for new column (remove trailing 's' if present)
    new_column = base[:-1] if base.endswith('s') and len(base) > 1 else base

    return lens_suffix, new_column


def parse_foreign_keys(sql_file: Path) -> Dict[str, Dict[str, Tuple[str, str]]]:
    """
    Parse foreign key relationships from SQL file.

    Returns:
        Dict mapping table_name -> {column_name -> (referenced_table, referenced_column)}
    """
    fk_map = defaultdict(dict)

    if not sql_file.exists():
        print(f"Warning: {sql_file} not found, skipping FK parsing")
        return fk_map

    content = sql_file.read_text()

    # Pattern: ALTER TABLE table_name ADD [CONSTRAINT name] FOREIGN KEY (column) REFERENCES ref_table(ref_column);
    pattern = r'ALTER\s+TABLE\s+(\w+)\s+ADD\s+(?:CONSTRAINT\s+\w+\s+)?FOREIGN\s+KEY\s*\((\w+)\)\s+REFERENCES\s+(\w+)\s*\((\w+)\)'

    for match in re.finditer(pattern, content, re.IGNORECASE):
        table_name = match.group(1)
        column_name = match.group(2)
        ref_table = match.group(3)
        ref_column = match.group(4)

        fk_map[table_name][column_name] = (ref_table, ref_column)

    return dict(fk_map)


def extract_namespace(uri: str) -> str:
    """Extract namespace from a URI (everything before the last # or /)."""
    if '#' in uri:
        return uri.rsplit('#', 1)[0] + '#'
    elif '/' in uri:
        return uri.rsplit('/', 1)[0] + '/'
    return uri


def load_property_types_from_ontology(ontology_file: Path) -> Dict[str, Dict[str, str]]:
    """
    Load property type information from OWL ontology.

    Parses the ontology to determine which predicates are ObjectProperty
    vs DatatypeProperty, and extracts the XSD datatype range for DatatypeProperties.

    Args:
        ontology_file: Path to the ontology file (.ttl or .owl)

    Returns:
        Dict mapping predicate_uri -> {'type': 'object'|'datatype', 'range': 'xsd:long'}
        Example: {
            'http://.../hasSamplingPoint': {'type': 'object', 'range': None},
            'http://.../hasSizeInBytes': {'type': 'datatype', 'range': 'xsd:long'}
        }
    """
    if not ontology_file.exists():
        print(f"Warning: Ontology file not found at {ontology_file}")
        return {}

    try:
        print(f"Loading ontology from {ontology_file}...")
        g = Graph()

        # Try to parse as Turtle first, then as OWL/XML
        try:
            g.parse(ontology_file, format="turtle")
        except Exception:
            g.parse(ontology_file, format="xml")

        property_types = {}
        XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema#"

        # Get all ObjectProperties and their ranges
        for prop in g.subjects(RDF.type, OWL.ObjectProperty):
            prop_uri = str(prop)
            property_types[prop_uri] = {'type': 'object', 'range': None}

            # Extract rdfs:range to get the target class
            for range_obj in g.objects(prop, RDFS.range):
                property_types[prop_uri]['range'] = str(range_obj)
                break  # Use first range declaration

        # Get all DatatypeProperties and their ranges
        for prop in g.subjects(RDF.type, OWL.DatatypeProperty):
            prop_uri = str(prop)
            property_types[prop_uri] = {'type': 'datatype', 'range': None}

            # Extract rdfs:range to get the XSD datatype
            for range_obj in g.objects(prop, RDFS.range):
                range_uri = str(range_obj)

                # Convert XSD URI to prefixed notation (e.g., xsd:long)
                if range_uri.startswith(XSD_NAMESPACE):
                    xsd_type = range_uri[len(XSD_NAMESPACE):]
                    property_types[prop_uri]['range'] = f"xsd:{xsd_type}"
                    break  # Use first range declaration

        obj_count = sum(1 for v in property_types.values() if v['type'] == 'object')
        data_count = sum(1 for v in property_types.values() if v['type'] == 'datatype')
        data_with_range = sum(1 for v in property_types.values() if v['type'] == 'datatype' and v['range'])

        print(f"  Found {obj_count} ObjectProperties and {data_count} DatatypeProperties")
        print(f"  {data_with_range} DatatypeProperties have explicit XSD ranges")

        return property_types

    except Exception as e:
        print(f"Warning: Error parsing ontology: {e}")
        print("  Falling back to FK-based property type detection")
        return {}


def get_prefix_name(namespace: str, known_prefixes: Dict[str, str]) -> str:
    """Get or generate a prefix name for a namespace."""
    # Well-known namespace mappings
    WELL_KNOWN_NAMESPACES = {
        'http://www.w3.org/1999/02/22-rdf-syntax-ns#': 'rdf',
        'http://www.w3.org/2000/01/rdf-schema#': 'rdfs',
        'http://www.w3.org/2002/07/owl#': 'owl',
        'http://www.w3.org/ns/prov#': 'prov',
        'http://www.w3.org/2001/XMLSchema#': 'xsd',
        'http://purl.org/dc/terms/': 'dcterms',
        'http://www.w3.org/ns/dcat#': 'dcat',
        'http://www.w3.org/2004/02/skos/core#': 'skos',
        'http://www.w3.org/ns/ssn/': 'ssn',
        'http://meta.icos-cp.eu/ontologies/cpmeta/': 'cpmeta',
        'http://meta.icos-cp.eu/resources/wdcgg/': 'wdcgg',
    }

    # Check well-known namespaces first
    if namespace in WELL_KNOWN_NAMESPACES:
        return WELL_KNOWN_NAMESPACES[namespace]

    # Check if we already have a mapping
    for prefix, ns in known_prefixes.items():
        if ns == namespace:
            return prefix

    # Generate a new prefix name from the URI
    if '#' in namespace:
        base = namespace.rsplit('#', 1)[0]
        prefix_candidate = base.rsplit('/', 1)[-1]
    else:
        base = namespace.rstrip('/')
        prefix_candidate = base.rsplit('/', 1)[-1]

    # Sanitize the prefix name
    prefix_candidate = sanitize_prefix_name(prefix_candidate)

    # Ensure uniqueness
    prefix = prefix_candidate
    counter = 1
    while prefix in known_prefixes or prefix in WELL_KNOWN_NAMESPACES.values():
        prefix = f"{prefix_candidate}{counter}"
        counter += 1

    return prefix


def sanitize_prefix_name(prefix: str) -> str:
    """
    Sanitize a prefix name to be valid for RDF/SPARQL/Ontop.

    Valid prefix: [a-zA-Z][a-zA-Z0-9_-]*
    """
    # Remove common URI patterns
    prefix = prefix.replace('http://', '').replace('https://', '')

    # Replace problematic characters with underscore
    # Keep letters, digits, hyphens, underscores
    sanitized = re.sub(r'[^a-zA-Z0-9_-]', '', prefix)

    # If it starts with a digit or hyphen, prepend 'ns'
    if sanitized and (sanitized[0].isdigit() or sanitized[0] in '-_'):
        sanitized = 'ns' + sanitized

    # If empty or still invalid, use a default
    if not sanitized or not sanitized[0].isalpha():
        sanitized = 'ns1'

    return sanitized


def generate_prefix_declarations(
    class_analysis: Dict,
    known_prefixes: Optional[Dict[str, str]] = None
) -> Tuple[str, Dict[str, str]]:
    """
    Generate the [PrefixDeclaration] section.

    Returns:
        Tuple of (declaration_text, prefix_to_namespace_map)
    """
    if known_prefixes is None:
        known_prefixes = {}

    namespaces = set()

    # Collect all namespaces from class URIs and predicates
    for class_info in class_analysis.get('classes', []):
        class_uri = class_info.get('class_uri', '')
        if class_uri:
            namespaces.add(extract_namespace(class_uri))

        for pred in class_info.get('predicates', []):
            pred_uri = pred.get('predicate_uri', '')
            if pred_uri:
                namespaces.add(extract_namespace(pred_uri))

    # Build prefix map
    prefix_map = {}
    for ns in sorted(namespaces):
        prefix = get_prefix_name(ns, prefix_map)
        prefix_map[prefix] = ns

    # Generate declaration text
    lines = ["[PrefixDeclaration]"]
    for prefix in sorted(prefix_map.keys()):
        lines.append(f"{prefix}:\t{prefix_map[prefix]}")

    # Add standard XSD prefix
    if 'xsd' not in prefix_map:
        lines.append("xsd:\thttp://www.w3.org/2001/XMLSchema#")
        prefix_map['xsd'] = "http://www.w3.org/2001/XMLSchema#"

    return '\n'.join(lines), prefix_map


def get_predicate_local_name(pred_uri: str) -> str:
    """Get the local name part of a predicate URI."""
    if '#' in pred_uri:
        return pred_uri.rsplit('#', 1)[1]
    elif '/' in pred_uri:
        return pred_uri.rsplit('/', 1)[1]
    return pred_uri


def get_prefixed_predicate(pred_uri: str, prefix_map: Dict[str, str]) -> str:
    """Convert a predicate URI to prefixed form (e.g., cpmeta:hasObjectSpec)."""
    namespace = extract_namespace(pred_uri)
    local_name = get_predicate_local_name(pred_uri)

    for prefix, ns in prefix_map.items():
        if ns == namespace:
            return f"{prefix}:{local_name}"

    return f"<{pred_uri}>"


def sanitize_column_name(pred_short: str) -> str:
    """
    Convert predicate short name or URI to column name.

    Handles both:
    - Prefixed names: 'cpmeta:hasObjectSpec' -> 'has_object_spec'
    - Full URIs: 'http://www.w3.org/ns/dcat#contactPoint' -> 'contact_point'
    - URL-encoded URIs: 'wdcgg:CONTACT%20POINT' -> 'contact_20_point'

    Note: % is replaced with _ literally (e.g., %20 -> _20_), not URL-decoded,
    to match the database column naming convention.
    """
    # Remove namespace prefix if using colon notation
    if ':' in pred_short:
        namespace, name = pred_short.split(':', 1)
    else:
        # For full URIs without namespace prefix, use the ENTIRE URL path
        # This handles cases like http://www.w3.org/ns/ssn/hasDeployment
        # which should become www_w3_org_ns_ssn_has_deployment
        if pred_short.startswith('http://') or pred_short.startswith('https://'):
            # Remove protocol
            name = pred_short.replace('http://', '').replace('https://', '')
        else:
            # Fallback: extract local name
            name = pred_short.split('/')[-1].split('#')[-1]
        namespace = ''

    # Replace % with _ (e.g., %20 becomes _20_)
    name = name.replace('%', '_')

    # Convert CamelCase to snake_case
    name = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', name)
    name = name.lower()

    # Clean up: replace all non-alphanumeric with underscores
    name = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    name = re.sub(r'_+', '_', name)
    name = name.strip('_')

    # Ensure doesn't start with digit
    if name and name[0].isdigit():
        name = f"col_{name}"

    # Ensure it's not a reserved word
    reserved_words = {'user', 'table', 'select', 'insert', 'update', 'delete', 'from', 'where', 'group', 'order', 'type'}
    if name in reserved_words:
        name = f"{name}_value"

    return name


def get_xsd_type(sql_type: str) -> Optional[str]:
    """Map SQL type to XSD type."""
    sql_type_lower = sql_type.upper()

    type_map = {
        'INTEGER': 'xsd:integer',
        'BIGINT': 'xsd:integer',
        'SMALLINT': 'xsd:integer',
        'REAL': 'xsd:float',
        'DOUBLE PRECISION': 'xsd:double',
        'NUMERIC': 'xsd:decimal',
        'BOOLEAN': 'xsd:boolean',
        'DATE': 'xsd:date',
        'TIMESTAMP': 'xsd:dateTime',
        'TIMESTAMP WITH TIME ZONE': 'xsd:dateTime',
    }

    for sql, xsd in type_map.items():
        if sql in sql_type_lower:
            return xsd

    # TEXT, VARCHAR, etc. don't need explicit XSD type
    return None


def find_referenced_table_prefix(
    ref_table: str,
    table_prefix_analysis: Dict
) -> Optional[str]:
    """Find the primary prefix for a referenced table."""
    # Handle both direct dict and wrapped structure
    tables = table_prefix_analysis.get('tables', table_prefix_analysis)

    if ref_table not in tables:
        return None

    prefix_counts = tables[ref_table].get('prefix_counts', {})
    if not prefix_counts:
        return None

    # Return the most common prefix
    return max(prefix_counts.items(), key=lambda x: x[1])[0]


def find_all_array_target_prefixes_from_references(
    class_uri: str,
    pred_uri: str,
    class_analysis: Dict,
    table_prefix_analysis: Dict
) -> List[Tuple[str, str]]:
    """
    Find ALL target prefixes for an array object property by analyzing reference patterns.

    Examines the 'references_to' section of class_predicates_analysis.json to determine
    which classes are referenced by this predicate, then returns prefixes for ALL targets.

    Args:
        class_uri: URI of the subject class (e.g., 'http://.../cpmeta/Collection')
        pred_uri: URI of the predicate (e.g., 'http://purl.org/dc/terms/hasPart')
        class_analysis: Loaded class_predicates_analysis.json
        table_prefix_analysis: Loaded table_prefix_analysis.json

    Returns:
        List of (prefix, target_class_name) tuples
        Example: [
            ('https://meta.icos-cp.eu/objects/', 'DataObject'),
            ('https://meta.icos-cp.eu/collections/', 'Collection')
        ]
    """
    # Find the class definition
    class_info = None
    for cls in class_analysis.get('classes', []):
        if cls.get('class_uri') == class_uri:
            class_info = cls
            break

    if not class_info:
        return []

    # Find ALL target classes for this predicate
    target_results = []

    for ref in class_info.get('references_to', []):
        target_class_uri = ref.get('class_uri')

        # Check if this reference uses our predicate
        for pred_ref in ref.get('predicates', []):
            if pred_ref.get('predicate_uri') == pred_uri:
                # Found a target class for this predicate
                # Convert target class URI to table name and prefix

                # First, find the class name
                target_class_name = None
                for cls in class_analysis.get('classes', []):
                    if cls.get('class_uri') == target_class_uri:
                        target_class_name = cls.get('class_name')
                        break

                # If class_name not found in classes list, use the one from reference
                if not target_class_name:
                    target_class_name = ref.get('class_name', target_class_uri)

                # Check if this class is part of a merged table
                target_table = None
                for merged_table, config in MERGE_GROUPS.items():
                    if target_class_name in config['classes']:
                        target_table = merged_table
                        break

                # If not merged, convert class name to table name
                if not target_table:
                    target_table = sanitize_table_name(target_class_name)

                # Get the primary prefix for this table
                target_prefix = find_referenced_table_prefix(target_table, table_prefix_analysis)

                if target_prefix:
                    # Extract short class name for mapping ID suffix
                    short_class_name = target_class_name.split(':')[-1] if ':' in target_class_name else target_class_name.split('/')[-1]
                    target_results.append((target_prefix, short_class_name))

                break  # Found the predicate in this reference, move to next reference

    return target_results


def generate_mapping_id(table_name: str, predicate_short: str, prefix: Optional[str] = None) -> str:
    """
    Generate a unique, valid mapping ID.

    Ensures the ID matches: [a-zA-Z][a-zA-Z0-9_]*
    """
    # Remove ct_ prefix from table name
    clean_table = table_name.replace('ct_', '')

    # Clean up predicate - extract local name if it's a full URI
    if predicate_short.startswith('http://') or predicate_short.startswith('https://'):
        # Get local name after last # or /
        if '#' in predicate_short:
            predicate_short = predicate_short.rsplit('#', 1)[1]
        elif '/' in predicate_short:
            predicate_short = predicate_short.rsplit('/', 1)[1]
    # If it's a prefixed name, remove namespace prefix
    elif ':' in predicate_short:
        predicate_short = predicate_short.split(':', 1)[1]

    # Replace % with _ (e.g., %20 becomes _20_)
    predicate_short = predicate_short.replace('%', '_')

    # Sanitize predicate to only alphanumeric and underscores (hyphens, spaces, etc. become underscores)
    predicate_short = re.sub(r'[^a-zA-Z0-9_]', '_', predicate_short)

    mapping_id = f"{clean_table}_{predicate_short}"

    if prefix:
        # Add a suffix based on the prefix to make it unique
        # Extract a meaningful short identifier from the prefix URL
        prefix_suffix = prefix.replace('http://', '').replace('https://', '')
        # Remove common domain parts to shorten
        prefix_suffix = prefix_suffix.replace('meta.icos-cp.eu/', '')
        prefix_suffix = prefix_suffix.replace('www.w3.org/', '')
        # Keep only alphanumeric characters
        prefix_suffix = re.sub(r'[^a-zA-Z0-9]', '', prefix_suffix)
        # Shorten to avoid overly long IDs
        prefix_suffix = prefix_suffix[:15]
        mapping_id += f"_{prefix_suffix}"

    # Collapse multiple underscores and strip leading/trailing underscores
    mapping_id = re.sub(r'_+', '_', mapping_id).strip('_')

    # Ensure it starts with a letter (should already be the case from table name)
    if mapping_id and not mapping_id[0].isalpha():
        mapping_id = 'map_' + mapping_id

    return mapping_id


def generate_table_mappings(
    table_name: str,
    class_info: Dict,
    table_prefixes: List[str],
    prefix_map: Dict[str, str],
    fk_map: Dict[str, Dict[str, Tuple[str, str]]],
    table_prefix_analysis: Dict,
    predicate_types: Dict[str, str],
    property_types_from_ontology: Dict[str, Dict[str, str]],
    cardinality_data: Dict[Tuple[str, str], Dict],
    functional_properties: Set[str],
    class_analysis: Dict
) -> Tuple[List[str], List[str], List[Dict]]:
    """
    Generate all mapping entries for a single table.

    Returns:
        Tuple of (scalar_mappings, array_mappings, lens_definitions)
    """
    scalar_mappings = []
    array_mappings = []
    lens_definitions = []
    class_uri = class_info.get('class_uri', '')

    # For multi-prefix tables, generate separate mappings
    if len(table_prefixes) > 1:
        for table_prefix in table_prefixes:
            scalar, array, lenses = _generate_mappings_for_prefix(
                table_name,
                class_info,
                table_prefix,
                prefix_map,
                fk_map,
                table_prefix_analysis,
                predicate_types,
                property_types_from_ontology,
                cardinality_data,
                functional_properties,
                class_analysis,
                multi_prefix=True
            )
            scalar_mappings.extend(scalar)
            array_mappings.extend(array)
            lens_definitions.extend(lenses)
    else:
        # Single prefix table
        table_prefix = table_prefixes[0] if table_prefixes else None
        scalar, array, lenses = _generate_mappings_for_prefix(
            table_name,
            class_info,
            table_prefix,
            prefix_map,
            fk_map,
            table_prefix_analysis,
            predicate_types,
            property_types_from_ontology,
            cardinality_data,
            functional_properties,
            class_analysis,
            multi_prefix=False
        )
        scalar_mappings.extend(scalar)
        array_mappings.extend(array)
        lens_definitions.extend(lenses)

    return scalar_mappings, array_mappings, lens_definitions


def _generate_mappings_for_prefix(
    table_name: str,
    class_info: Dict,
    table_prefix: Optional[str],
    prefix_map: Dict[str, str],
    fk_map: Dict[str, Dict[str, Tuple[str, str]]],
    table_prefix_analysis: Dict,
    predicate_types: Dict[str, str],
    property_types_from_ontology: Dict[str, Dict[str, str]],
    cardinality_data: Dict[Tuple[str, str], Dict],
    functional_properties: Set[str],
    class_analysis: Dict,
    multi_prefix: bool = False
) -> Tuple[List[str], List[str], List[Dict]]:
    """
    Generate mappings for a table with a specific prefix.

    Returns:
        Tuple of (scalar_mappings, array_mappings, lens_definitions)
    """
    scalar_mappings = []
    array_mappings = []
    lens_definitions = []

    # Get table's foreign keys
    table_fks = fk_map.get(table_name, {})

    # Get class URI(s) for array detection
    # For merged tables, check cardinality against ALL original class URIs
    original_class_uris = class_info.get('original_class_uris', [class_info.get('class_uri', '')])
    if not original_class_uris:
        original_class_uris = [class_info.get('class_uri', '')]

    # Generate mapping for each predicate
    for pred in class_info.get('predicates', []):
        pred_uri = pred.get('predicate_uri', '')
        pred_short = pred.get('predicate_short', '')

        if not pred_uri or not pred_short:
            continue

        # Skip rdf:type predicate
        if pred_uri == 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type':
            continue

        # Convert predicate to column name
        column_name = sanitize_column_name(pred_short)

        # Determine if this predicate is an array
        # Use same logic as generate_class_tables.py
        is_functional = pred_uri in functional_properties
        if is_functional:
            # Functional property - always scalar
            is_array = False
        else:
            # Check actual cardinality data
            # For merged classes, check ALL original class URIs and take maximum
            max_values = 1
            for uri in original_class_uris:
                stats = cardinality_data.get((uri, pred_uri), {})
                max_values = max(max_values, stats.get('max_values', 1))
            is_array = max_values > 1

        # Check if this is a foreign key
        is_fk = column_name in table_fks

        # Determine property type and range from ontology
        ontology_info = property_types_from_ontology.get(pred_uri, {})
        property_type = ontology_info.get('type')  # 'object' or 'datatype'
        ontology_xsd_range = ontology_info.get('range')  # e.g., 'xsd:long'

        is_object_property = property_type == 'object'

        # Generate mapping ID
        mapping_id = generate_mapping_id(
            table_name,
            pred_short,
            table_prefix if multi_prefix else None
        )

        # Build target triple
        if table_prefix:
            subject_uri = f"<{table_prefix}{{id}}>"
        else:
            # Fallback: use prefix column
            subject_uri = "<{prefix}{id}>"

        predicate = get_prefixed_predicate(pred_uri, prefix_map)

        # For object properties, determine ref_prefix from FK (for both scalar and array)
        ref_prefix = None
        if is_object_property and is_fk:
            ref_table, ref_column = table_fks[column_name]
            ref_prefix = find_referenced_table_prefix(ref_table, table_prefix_analysis)

        if is_object_property:
            # Object property - reconstruct referenced URI
            if ref_prefix:
                object_part = f"<{ref_prefix}{{{column_name}}}>"
            else:
                # Fallback: assume column contains full URI or needs prefix from somewhere
                # This handles cases where there's no FK but ontology declares it as ObjectProperty
                object_part = f"<{{{column_name}}}>"
        else:
            # Datatype property (or unknown - default to datatype)
            # IMPORTANT: Use XSD type from ontology if available, otherwise infer from SQL type
            if ontology_xsd_range:
                # Use the exact XSD type declared in the ontology
                object_part = f"{{{column_name}}}^^{ontology_xsd_range}"
            else:
                # Fallback: infer XSD type from SQL type
                sql_type = predicate_types.get(pred_uri, 'TEXT')
                xsd_type = get_xsd_type(sql_type)

                if xsd_type:
                    object_part = f"{{{column_name}}}^^{xsd_type}"
                else:
                    object_part = f"{{{column_name}}}"

        target = f"{subject_uri} {predicate} {object_part} ."

        # Build source SQL
        source_lines = [
            "SELECT",
            f"    id,",
            f"    {column_name}"
        ]

        if not table_prefix:
            # Need prefix column for URI reconstruction
            source_lines.insert(2, "    prefix,")

        # Use BasicLens instead of direct table reference
        basic_lens_name = get_basic_lens_name(table_name)
        source_lines.append(f"FROM lenses.{basic_lens_name}")

        if multi_prefix and table_prefix:
            source_lines.append(f"WHERE prefix = '{table_prefix}'")

        source = '\n            '.join(source_lines)

        # Add to appropriate list based on whether it's an array
        if is_array:
            # Create lens for array column
            lens_suffix, new_column = create_lens_names(column_name)
            lens_name = f"{table_name}_{lens_suffix}"

            # If flattened column name equals new column name, add "single_" prefix
            if column_name == new_column:
                new_column = f"single_{new_column}"

            # Create lens definition
            lens_def = {
                "name": ["lenses", lens_name],
                "type": "FlattenLens",
                "baseRelation": [table_name],
                "flattenedColumn": {
                    "name": column_name,
                    "datatype": "TEXT[]"
                },
                "columns": {
                    "kept": ["id"],
                    "new": new_column,
                    "position": "index"
                }
            }

            # Add iriSafeConstraints based on property type
            if is_object_property:
                # Object properties need both id and the flattened column
                lens_def["iriSafeConstraints"] = {"added": ["id", new_column]}
            else:
                # Datatype properties only need id
                lens_def["iriSafeConstraints"] = {"added": ["id"]}

            # nonNullConstraints always includes both id and the flattened column
            lens_def["nonNullConstraints"] = {"added": ["id", new_column]}

            lens_definitions.append(lens_def)

            # Build modified source SQL using the lens
            source_lines = [
                "SELECT",
                f"    id,",
                f"    {new_column}"
            ]

            if not table_prefix:
                # For multi-prefix tables, we need to join back to get prefix
                # For now, skip prefix column for lens-based queries
                pass

            source_lines.append(f"FROM lenses.{lens_name}")

            if multi_prefix and table_prefix:
                # For multi-prefix lenses, we'd need to filter in the lens or join
                # For simplicity, we'll note this limitation
                pass

            source = '\n            '.join(source_lines)

            # Rebuild target with singular column name
            if table_prefix:
                subject_uri = f"<{table_prefix}{{id}}>"
            else:
                subject_uri = "<{prefix}{id}>"

            predicate = get_prefixed_predicate(pred_uri, prefix_map)

            # For array object properties, determine ALL target prefixes and generate multiple mappings
            if is_object_property:
                # Collect all possible target prefixes
                target_prefixes = []  # List of (prefix, class_name) tuples

                # Strategy 1: Get the range class from ontology
                range_class_uri = ontology_info.get('range')

                if range_class_uri:
                    # Find which table corresponds to this class URI
                    ref_class_name = None
                    for cls in class_analysis.get('classes', []):
                        if cls.get('class_uri') == range_class_uri:
                            ref_class_name = cls.get('class_name', '')
                            break

                    if ref_class_name:
                        # Check if this class is part of a merged table
                        ref_table = None
                        for merged_table, config in MERGE_GROUPS.items():
                            if ref_class_name in config['classes']:
                                ref_table = merged_table
                                break

                        # If not merged, convert class name to table name
                        if not ref_table:
                            ref_table = sanitize_table_name(ref_class_name)

                        # Get the first prefix for this table
                        if ref_table in table_prefix_analysis.get('tables', {}):
                            table_data = table_prefix_analysis['tables'][ref_table]
                            prefixes = table_data.get('prefix_counts', {})
                            if prefixes:
                                array_ref_prefix = list(prefixes.keys())[0]
                                # Extract short class name
                                short_class_name = ref_class_name.split(':')[-1] if ':' in ref_class_name else ref_class_name
                                target_prefixes.append((array_ref_prefix, short_class_name))

                # Strategy 2: Analyze reference patterns from class_predicates_analysis.json
                if not target_prefixes:
                    # Check all original class URIs (for merged tables)
                    for uri in original_class_uris:
                        prefixes_from_refs = find_all_array_target_prefixes_from_references(
                            uri, pred_uri, class_analysis, table_prefix_analysis
                        )
                        target_prefixes.extend(prefixes_from_refs)

                    # Deduplicate by prefix (keep first occurrence)
                    seen_prefixes = set()
                    unique_target_prefixes = []
                    for prefix, class_name in target_prefixes:
                        if prefix not in seen_prefixes:
                            seen_prefixes.add(prefix)
                            unique_target_prefixes.append((prefix, class_name))
                    target_prefixes = unique_target_prefixes

                # Strategy 3: Use FK-based prefix if available (fallback)
                if not target_prefixes and ref_prefix:
                    # Extract a reasonable class name from the referenced table
                    target_class_name = "Unknown"
                    target_prefixes.append((ref_prefix, target_class_name))

                # Generate a separate mapping for EACH target prefix
                if target_prefixes:
                    for array_ref_prefix, target_class_name in target_prefixes:
                        # Create unique mapping_id with suffix if multiple targets
                        if len(target_prefixes) > 1:
                            # Sanitize class name for use in mapping ID
                            sanitized_class = re.sub(r'[^a-zA-Z0-9_]', '', target_class_name)
                            mapping_id_with_suffix = f"{mapping_id}_{sanitized_class}"
                        else:
                            mapping_id_with_suffix = mapping_id

                        object_part = f"<{array_ref_prefix}{{{new_column}}}>"
                        target = f"{subject_uri} {predicate} {object_part} ."

                        # Combine into mapping entry
                        mapping = f"""mappingId\t{mapping_id_with_suffix}
target\t\t{target}
source\t\t{source}"""

                        array_mappings.append(mapping)
                else:
                    # Warning: no prefix found
                    print(f"WARNING: Could not determine prefix for array object property {pred_short} in {table_name}")
                    print(f"  Predicate URI: {pred_uri}")
                    print(f"  Column: {new_column}")
                    # Still create a mapping without prefix as fallback
                    object_part = f"<{{{new_column}}}>"
                    target = f"{subject_uri} {predicate} {object_part} ."
                    mapping = f"""mappingId\t{mapping_id}
target\t\t{target}
source\t\t{source}"""
                    array_mappings.append(mapping)
            else:
                # Datatype property - single mapping
                if ontology_xsd_range:
                    object_part = f"{{{new_column}}}^^{ontology_xsd_range}"
                else:
                    sql_type = predicate_types.get(pred_uri, 'TEXT')
                    xsd_type = get_xsd_type(sql_type)
                    if xsd_type:
                        object_part = f"{{{new_column}}}^^{xsd_type}"
                    else:
                        object_part = f"{{{new_column}}}"

                target = f"{subject_uri} {predicate} {object_part} ."

                # Combine into mapping entry
                mapping = f"""mappingId\t{mapping_id}
target\t\t{target}
source\t\t{source}"""

                array_mappings.append(mapping)
        else:
            # Scalar mapping - use original logic
            mapping = f"""mappingId\t{mapping_id}
target\t\t{target}
source\t\t{source}"""

            scalar_mappings.append(mapping)

    return scalar_mappings, array_mappings, lens_definitions


def merge_class_predicates(class_infos: List[Dict]) -> Dict:
    """
    Merge predicates from multiple classes into a single class info dict.

    Used for union/merged tables that combine multiple OWL classes.
    """
    if not class_infos:
        return {'predicates': []}

    # Use the first class as the base
    merged = {
        'class_name': 'MERGED:' + '+'.join(c.get('class_name', '') for c in class_infos),
        'class_uri': class_infos[0].get('class_uri', ''),
        'original_class_uris': [c.get('class_uri', '') for c in class_infos],  # Keep all URIs for cardinality checks
        'predicates': []
    }

    # Collect all unique predicates by URI
    seen_predicates = {}

    for class_info in class_infos:
        for pred in class_info.get('predicates', []):
            pred_uri = pred.get('predicate_uri', '')
            if pred_uri and pred_uri not in seen_predicates:
                seen_predicates[pred_uri] = pred

    merged['predicates'] = list(seen_predicates.values())

    return merged


def main():
    """Main entry point."""
    # Paths
    script_dir = Path(__file__).parent
    class_analysis_file = script_dir / 'class_predicates_analysis.json'
    table_prefix_file = script_dir / 'table_prefix_analysis.json'
    fk_sql_file = script_dir / 'class_tables' / 'create_foreign_keys.sql'
    predicate_types_file = script_dir / 'predicate_types.json'
    output_file = script_dir.parent / 'ontop' / 'mapping' / 'generated_all_mappings.obda'

    # Load input files
    print("Loading input files...")
    with open(class_analysis_file) as f:
        class_analysis = json.load(f)

    with open(table_prefix_file) as f:
        table_prefix_analysis = json.load(f)

    if predicate_types_file.exists():
        with open(predicate_types_file) as f:
            predicate_types_data = json.load(f)
            # Extract the types dict from the structure
            predicate_types = {}
            if 'types' in predicate_types_data:
                for pred_uri, info in predicate_types_data['types'].items():
                    predicate_types[pred_uri] = info.get('postgresql_type', 'TEXT')
            else:
                predicate_types = predicate_types_data
    else:
        print(f"Warning: {predicate_types_file} not found, using default types")
        predicate_types = {}

    # Load property types from ontology
    print("\nLoading property types from ontology...")
    ontology_file = script_dir.parent / 'ontop' / 'cpmeta.ttl'
    property_types_from_ontology = load_property_types_from_ontology(ontology_file)

    # Load cardinality data for array detection
    print("\nLoading cardinality analysis...")
    cardinality_file = script_dir / 'predicate_cardinality.json'
    if cardinality_file.exists():
        cardinality_data = load_cardinality_analysis(str(cardinality_file))
        print(f"Loaded cardinality data for {len(cardinality_data)} (class, predicate) pairs")
    else:
        print(f"Warning: {cardinality_file} not found - will not detect array columns")
        cardinality_data = {}

    # Detect functional properties from ontology
    print("\nDetecting functional properties from ontology...")
    functional_properties, non_functional_properties = detect_functional_properties(str(ontology_file))
    print(f"Found {len(functional_properties)} functional properties")
    print(f"Found {len(non_functional_properties)} non-functional properties")

    # Parse foreign keys
    print("\nParsing foreign keys...")
    fk_map = parse_foreign_keys(fk_sql_file)
    print(f"Found {sum(len(fks) for fks in fk_map.values())} foreign key relationships")

    # Generate prefix declarations
    print("Generating prefix declarations...")
    prefix_section, prefix_map = generate_prefix_declarations(class_analysis)

    # Build table name to class info map
    print("Building table to class mappings...")
    table_to_class = {}
    table_to_classes = {}  # For merged tables: table -> [class_info1, class_info2, ...]
    class_name_to_info = {}  # class_name -> class_info

    # First pass: build class_name -> class_info lookup
    for class_info in class_analysis.get('classes', []):
        class_name = class_info.get('class_name', '')
        if class_name:
            class_name_to_info[class_name] = class_info

    # Second pass: handle merged tables from MERGE_GROUPS
    for merged_table, config in MERGE_GROUPS.items():
        table_to_classes[merged_table] = []
        for class_name in config['classes']:
            if class_name in class_name_to_info:
                class_info = class_name_to_info[class_name]
                table_to_classes[merged_table].append(class_info)
                # Set first class as the single representative
                if merged_table not in table_to_class:
                    table_to_class[merged_table] = class_info

    # Third pass: handle non-merged tables (standard case)
    for class_info in class_analysis.get('classes', []):
        class_name = class_info.get('class_name', '')
        if class_name:
            table_name = sanitize_table_name(class_name)
            # Only add if not already handled by MERGE_GROUPS
            if table_name not in table_to_class:
                table_to_class[table_name] = class_info
                table_to_classes[table_name] = [class_info]

    # Generate mappings for all tables
    print("Generating mappings...")
    all_scalar_mappings = []
    all_array_mappings = []
    all_lens_definitions = []
    created_basic_lenses = set()  # Track which BasicLens we've already created

    # Get tables from the structure (it has a 'tables' key)
    tables = table_prefix_analysis.get('tables', table_prefix_analysis)

    for table_name in sorted(tables.keys()):
        if table_name not in table_to_class:
            print(f"Warning: No class info for table {table_name}, skipping")
            continue

        prefix_counts = tables[table_name].get('prefix_counts', {})
        table_prefixes = list(prefix_counts.keys())

        if not table_prefixes:
            print(f"Warning: No prefixes for table {table_name}, skipping")
            continue

        # Handle merged tables (multiple classes -> one table)
        classes_for_table = table_to_classes.get(table_name, [table_to_class[table_name]])

        if len(classes_for_table) > 1:
            # Merged table - combine predicates from all classes
            merged_class_info = merge_class_predicates(classes_for_table)
            print(f"  {table_name}: {len(table_prefixes)} prefix(es), {len(merged_class_info.get('predicates', []))} predicate(s) [MERGED from {len(classes_for_table)} classes]")
            class_info = merged_class_info
        else:
            # Single class table
            class_info = table_to_class[table_name]
            print(f"  {table_name}: {len(table_prefixes)} prefix(es), {len(class_info.get('predicates', []))} predicate(s)")

        # Create BasicLens for this table if not already created
        if table_name not in created_basic_lenses:
            basic_lens_name = get_basic_lens_name(table_name)
            basic_lens_def = {
                "type": "BasicLens",
                "baseRelation": [table_name],
                "name": ["lenses", basic_lens_name],
                "iriSafeConstraints": {
                    "added": ["id"]
                },
                "nonNullConstraints": {
                    "added": ["id"]
                }
            }
            # Insert BasicLens at the beginning to ensure they come before FlattenLens
            all_lens_definitions.insert(0, basic_lens_def)
            created_basic_lenses.add(table_name)

        scalar_mappings, array_mappings, lens_definitions = generate_table_mappings(
            table_name,
            class_info,
            table_prefixes,
            prefix_map,
            fk_map,
            table_prefix_analysis,
            predicate_types,
            property_types_from_ontology,
            cardinality_data,
            functional_properties,
            class_analysis
        )

        all_scalar_mappings.extend(scalar_mappings)
        all_array_mappings.extend(array_mappings)
        all_lens_definitions.extend(lens_definitions)

    # Write output files
    print(f"\nWriting mappings to output files...")
    output_file.parent.mkdir(parents=True, exist_ok=True)

    # Combine all mappings (scalar + array) into a single file
    all_mappings = all_scalar_mappings + all_array_mappings
    print(f"  Writing {len(all_mappings)} mappings ({len(all_scalar_mappings)} scalar, {len(all_array_mappings)} array) to {output_file}...")
    with open(output_file, 'w') as f:
        f.write(prefix_section)
        f.write('\n\n')
        f.write('[MappingDeclaration] @collection [[\n\n')
        f.write('\n\n'.join(all_mappings))
        f.write('\n\n]]')

    # Deduplicate lenses by name (multi-prefix tables generate duplicates)
    seen_lenses = {}
    for lens in all_lens_definitions:
        lens_key = tuple(lens['name'])
        if lens_key not in seen_lenses:
            seen_lenses[lens_key] = lens

    deduped_lens_definitions = list(seen_lenses.values())

    # Write lenses.json file (in ontop/ directory, not ontop/mapping/)
    lenses_output_file = output_file.parent.parent / 'lenses.json'
    print(f"  Writing {len(deduped_lens_definitions)} lens definitions to {lenses_output_file}...")
    if len(all_lens_definitions) > len(deduped_lens_definitions):
        print(f"    (Removed {len(all_lens_definitions) - len(deduped_lens_definitions)} duplicates from multi-prefix tables)")
    lenses_data = {
        "relations": deduped_lens_definitions
    }
    with open(lenses_output_file, 'w') as f:
        json.dump(lenses_data, f, indent=4)

    # Validate array object properties have prefixes
    print(f"\n=== Array Object Property Validation ===")
    missing_prefix_mappings = []
    for mapping in all_array_mappings:
        # Check for patterns like <{variable}> at the end of target (without a prefix)
        # Pattern: target line ending with <{...}> .
        if re.search(r'target\s+.*<\{[^}]+\}>\s*\.$', mapping, re.MULTILINE):
            # Extract mapping ID for reporting
            mapping_id_match = re.search(r'mappingId\s+(\S+)', mapping)
            if mapping_id_match:
                missing_prefix_mappings.append(mapping_id_match.group(1))

    if missing_prefix_mappings:
        print(f"⚠ WARNING: {len(missing_prefix_mappings)} array object properties missing URI prefixes:")
        for mapping_id in missing_prefix_mappings[:10]:  # Show first 10
            print(f"  - {mapping_id}")
        if len(missing_prefix_mappings) > 10:
            print(f"  ... and {len(missing_prefix_mappings) - 10} more")
        print(f"\nPlease review these mappings and add explicit ontology ranges or reference data.")
    else:
        print(f"✓ All {len(all_array_mappings)} array object properties have URI prefixes")

    print(f"\nDone! Generated {len(all_mappings)} total mappings ({len(all_scalar_mappings)} scalar, {len(all_array_mappings)} array) and {len(deduped_lens_definitions)} lenses")
    print(f"All mappings: {output_file}")
    print(f"Lenses: {lenses_output_file}")


if __name__ == '__main__':
    main()
