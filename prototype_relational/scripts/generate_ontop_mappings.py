#!/usr/bin/env python3
"""
Generate Ontop mappings for all SQL class tables from class predicates analysis.

This script reads:
- class_predicates_analysis.json: Class and predicate metadata
- table_prefix_analysis.json: Table to prefix mappings
- class_tables/create_foreign_keys.sql: Foreign key relationships
- predicate_types.json: SQL types for proper literal handling

Outputs:
- ../ontop/mapping/generated_all_mappings.obda: Complete Ontop R2RML mappings
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from collections import defaultdict

# Table merge configuration (from generate_class_tables.py)
# Maps merged table names to the classes that were merged into them
MERGE_GROUPS = {
    'ct_object_specs': ['cpmeta:SimpleObjectSpec', 'cpmeta:DataObjectSpec'],
    'ct_spatial_coverages': ['cpmeta:SpatialCoverage', 'cpmeta:LatLonBox', 'cpmeta:Position'],
    'ct_organizations': ['cpmeta:Organization', 'cpmeta:TC', 'cpmeta:Facility'],
    'ct_stations': ['cpmeta:Station', 'cpmeta:AS', 'cpmeta:ES', 'cpmeta:OS',
                    'cpmeta:SailDrone', 'cpmeta:IngosStation', 'cpmeta:AtmoStation'],
}


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
    """
    # If it's a full URI, extract just the local name
    if pred_short.startswith('http://') or pred_short.startswith('https://'):
        # Get local name after last # or /
        if '#' in pred_short:
            pred_short = pred_short.rsplit('#', 1)[1]
        elif '/' in pred_short:
            pred_short = pred_short.rsplit('/', 1)[1]
    # If it's a prefixed name, remove namespace prefix
    elif ':' in pred_short:
        pred_short = pred_short.split(':', 1)[1]

    # Convert to snake_case
    name = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', pred_short)
    name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', name)
    return name.lower()


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

    # Sanitize predicate to only alphanumeric and underscores
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
    predicate_types: Dict[str, str]
) -> List[str]:
    """
    Generate all mapping entries for a single table.

    Returns:
        List of mapping text blocks
    """
    mappings = []
    class_uri = class_info.get('class_uri', '')

    # For multi-prefix tables, generate separate mappings
    if len(table_prefixes) > 1:
        for table_prefix in table_prefixes:
            mappings.extend(
                _generate_mappings_for_prefix(
                    table_name,
                    class_info,
                    table_prefix,
                    prefix_map,
                    fk_map,
                    table_prefix_analysis,
                    predicate_types,
                    multi_prefix=True
                )
            )
    else:
        # Single prefix table
        table_prefix = table_prefixes[0] if table_prefixes else None
        mappings.extend(
            _generate_mappings_for_prefix(
                table_name,
                class_info,
                table_prefix,
                prefix_map,
                fk_map,
                table_prefix_analysis,
                predicate_types,
                multi_prefix=False
            )
        )

    return mappings


def _generate_mappings_for_prefix(
    table_name: str,
    class_info: Dict,
    table_prefix: Optional[str],
    prefix_map: Dict[str, str],
    fk_map: Dict[str, Dict[str, Tuple[str, str]]],
    table_prefix_analysis: Dict,
    predicate_types: Dict[str, str],
    multi_prefix: bool = False
) -> List[str]:
    """Generate mappings for a table with a specific prefix."""
    mappings = []

    # Get table's foreign keys
    table_fks = fk_map.get(table_name, {})

    # Generate mapping for each predicate
    for pred in class_info.get('predicates', []):
        pred_uri = pred.get('predicate_uri', '')
        pred_short = pred.get('predicate_short', '')

        if not pred_uri or not pred_short:
            continue

        # Convert predicate to column name
        column_name = sanitize_column_name(pred_short)

        # Check if this is a foreign key
        is_fk = column_name in table_fks

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

        if is_fk:
            # Object property - reconstruct referenced URI
            ref_table, ref_column = table_fks[column_name]
            ref_prefix = find_referenced_table_prefix(ref_table, table_prefix_analysis)

            if ref_prefix:
                object_part = f"<{ref_prefix}{{{column_name}}}>"
            else:
                # Fallback: can't determine prefix
                object_part = f"<{{{column_name}}}>"
        else:
            # Datatype property
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

        source_lines.append(f"FROM {table_name}")

        if multi_prefix and table_prefix:
            source_lines.append(f"WHERE prefix = '{table_prefix}'")

        source = '\n            '.join(source_lines)

        # Combine into mapping entry
        mapping = f"""mappingId\t{mapping_id}
target\t\t{target}
source\t\t{source}"""

        mappings.append(mapping)

    return mappings


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

    # Parse foreign keys
    print("Parsing foreign keys...")
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
    for merged_table, class_names in MERGE_GROUPS.items():
        table_to_classes[merged_table] = []
        for class_name in class_names:
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
    all_mappings = []

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

        mappings = generate_table_mappings(
            table_name,
            class_info,
            table_prefixes,
            prefix_map,
            fk_map,
            table_prefix_analysis,
            predicate_types
        )

        all_mappings.extend(mappings)

    # Write output file
    print(f"\nWriting {len(all_mappings)} mappings to {output_file}...")
    output_file.parent.mkdir(parents=True, exist_ok=True)

    with open(output_file, 'w') as f:
        f.write(prefix_section)
        f.write('\n\n')
        f.write('[MappingDeclaration] @collection [[\n\n')
        f.write('\n\n'.join(all_mappings))
        f.write('\n\n]]')

    print(f"Done! Generated {len(all_mappings)} mappings")
    print(f"Output: {output_file}")


if __name__ == '__main__':
    main()
