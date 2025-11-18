#!/usr/bin/env python3
"""
Generate database tables from class_predicates_analysis.json

This script reads the class_predicates_analysis.json file and creates one table per OWL class.
Each table has:
- id column (subject URI)
- columns for predicates used by that class
- foreign key columns for relationships to other classes

Column types are inferred by analyzing actual values in the rdf_triples table.
"""

import json
import re
import argparse
import sys
from pathlib import Path
from collections import defaultdict
from typing import Dict, Set, List, Tuple, Optional
import psycopg2
from psycopg2 import sql


# Namespace definitions for shortening
NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'xsd': 'http://www.w3.org/2001/XMLSchema#',
    'cpmeta': 'http://meta.icos-cp.eu/ontologies/cpmeta/',
    'prov': 'http://www.w3.org/ns/prov#',
    'purl': 'http://purl.org/dc/terms/',
    'dcat': 'http://www.w3.org/ns/prov#',
}


# Table merge configuration - defines which classes should be merged into union tables
# This resolves polymorphic foreign key issues where one column references multiple table types
MERGE_GROUPS = {
    'ct_object_specs': {
        'classes': ['cpmeta:SimpleObjectSpec', 'cpmeta:DataObjectSpec'],
        'type_column': 'spec_type',
        'type_values': {
            'cpmeta:SimpleObjectSpec': 'simple',
            'cpmeta:DataObjectSpec': 'data'
        }
    },
    'ct_spatial_coverages': {
        'classes': ['cpmeta:SpatialCoverage', 'cpmeta:LatLonBox', 'cpmeta:Position'],
        'type_column': 'coverage_type',
        'type_values': {
            'cpmeta:SpatialCoverage': 'spatial',
            'cpmeta:LatLonBox': 'latlon',
            'cpmeta:Position': 'position'
        }
    },
    'ct_organizations': {
        'classes': ['cpmeta:Organization', 'cpmeta:TC', 'cpmeta:Facility'],
        'type_column': 'org_type',
        'type_values': {
            'cpmeta:Organization': 'organization',
            'cpmeta:TC': 'thematic_center',
            'cpmeta:Facility': 'central_facility'
        }
    },
    'ct_stations': {
        'classes': ['cpmeta:Station', 'cpmeta:AS', 'cpmeta:ES', 'cpmeta:OS',
                   'cpmeta:SailDrone', 'cpmeta:IngosStation', 'cpmeta:AtmoStation'],
        'type_column': 'station_type',
        'type_values': {
            'cpmeta:Station': 'station',
            'cpmeta:AS': 'as',
            'cpmeta:ES': 'es',
            'cpmeta:OS': 'os',
            'cpmeta:SailDrone': 'saildrone',
            'cpmeta:IngosStation': 'ingos',
            'cpmeta:AtmoStation': 'atmo'
        }
    },
    'ct_dataset_specs': {
        'classes': ['cpmeta:DatasetSpec', 'cpmeta:TabularDatasetSpec'],
        'type_column': 'dataset_type',
        'type_values': {
            'cpmeta:DatasetSpec': 'dataset',
            'cpmeta:TabularDatasetSpec': 'tabular'
        }
    }
}


def load_analysis_json(json_path: str) -> dict:
    """Load and parse the class_predicates_analysis.json file"""
    with open(json_path, 'r') as f:
        return json.load(f)


def load_predicate_types(json_path: str) -> Dict[str, str]:
    """
    Load predicate type mappings from JSON

    Returns: Dict[predicate_uri] -> postgresql_type
    """
    with open(json_path, 'r') as f:
        data = json.load(f)

    type_map = {}
    for pred_uri, pred_info in data.get('types', {}).items():
        type_map[pred_uri] = pred_info['postgresql_type']

    return type_map


def build_class_uri_map(classes: List[dict]) -> Dict[str, str]:
    """
    Build a mapping from class short names to full URIs

    Returns: Dict[class_short_name] -> class_uri
    """
    class_uri_map = {}
    for class_data in classes:
        class_name = class_data['class_name']
        class_uri = class_data['class_uri']
        class_uri_map[class_name] = class_uri
    return class_uri_map


def get_merge_mapping() -> Tuple[Dict[str, str], Dict[str, dict]]:
    """
    Build mappings for table merges

    Returns:
        - Dict[class_name] -> union_table_name (for classes that should be merged)
        - Dict[union_table_name] -> merge_config (config for each union table)
    """
    class_to_union = {}
    union_configs = {}

    for union_table, config in MERGE_GROUPS.items():
        union_configs[union_table] = config
        for class_name in config['classes']:
            class_to_union[class_name] = union_table

    return class_to_union, union_configs


def merge_classes(classes: List[dict], class_uri_map: Dict[str, str]) -> List[dict]:
    """
    Merge classes according to MERGE_GROUPS configuration

    Creates union tables that combine multiple related classes with a type discriminator column.

    Args:
        classes: List of class data dictionaries
        class_uri_map: Mapping from class short names to URIs

    Returns:
        List of class data with merged entries
    """
    class_to_union, union_configs = get_merge_mapping()

    # Build a map of class_uri -> class_data for quick lookup
    uri_to_class = {c['class_uri']: c for c in classes}

    # Track which classes have been merged
    merged_class_uris = set()
    merged_classes = []

    # Create merged class entries
    for union_table, config in union_configs.items():
        # Collect all classes to merge
        classes_to_merge = []
        for class_name in config['classes']:
            if class_name in class_uri_map:
                class_uri = class_uri_map[class_name]
                if class_uri in uri_to_class:
                    classes_to_merge.append(uri_to_class[class_uri])
                    merged_class_uris.add(class_uri)

        if not classes_to_merge:
            continue

        # Use the first class as the base and merge others into it
        base_class = classes_to_merge[0].copy()
        base_class['class_name'] = f"MERGED:{union_table}"
        base_class['merged_from'] = [c['class_name'] for c in classes_to_merge]
        base_class['merge_config'] = config

        # Combine instance counts
        base_class['instance_count'] = sum(c['instance_count'] for c in classes_to_merge)

        # Merge predicates - combine all unique predicates
        predicates_map = {}
        for cls in classes_to_merge:
            for pred in cls.get('predicates', []):
                pred_uri = pred['predicate_uri']
                if pred_uri not in predicates_map:
                    predicates_map[pred_uri] = pred.copy()
                else:
                    # Update counts if this predicate appears in multiple classes
                    existing = predicates_map[pred_uri]
                    existing['count'] = existing.get('count', 0) + pred.get('count', 0)

        base_class['predicates'] = list(predicates_map.values())

        # Merge references_to - combine all unique references
        references_map = {}
        for cls in classes_to_merge:
            for ref in cls.get('references_to', []):
                ref_class_uri = ref['class_uri']
                if ref_class_uri not in references_map:
                    references_map[ref_class_uri] = {
                        'class_uri': ref_class_uri,
                        'class_name': ref['class_name'],
                        'predicates': []
                    }

                # Add predicates for this reference
                pred_map = {p['predicate_uri']: p for p in references_map[ref_class_uri]['predicates']}
                for pred in ref.get('predicates', []):
                    pred_uri = pred['predicate_uri']
                    if pred_uri not in pred_map:
                        pred_map[pred_uri] = pred.copy()
                    else:
                        # Update counts
                        pred_map[pred_uri]['count'] = pred_map[pred_uri].get('count', 0) + pred.get('count', 0)

                references_map[ref_class_uri]['predicates'] = list(pred_map.values())

        base_class['references_to'] = list(references_map.values())

        # Merge referenced_by - combine all unique back-references
        referenced_by_map = {}
        for cls in classes_to_merge:
            for ref in cls.get('referenced_by', []):
                ref_class_uri = ref['class_uri']
                if ref_class_uri not in referenced_by_map:
                    referenced_by_map[ref_class_uri] = {
                        'class_uri': ref_class_uri,
                        'class_name': ref['class_name'],
                        'predicates': []
                    }

                # Add predicates for this back-reference
                pred_map = {p['predicate_uri']: p for p in referenced_by_map[ref_class_uri]['predicates']}
                for pred in ref.get('predicates', []):
                    pred_uri = pred['predicate_uri']
                    if pred_uri not in pred_map:
                        pred_map[pred_uri] = pred.copy()
                    else:
                        pred_map[pred_uri]['count'] = pred_map[pred_uri].get('count', 0) + pred.get('count', 0)

                referenced_by_map[ref_class_uri]['predicates'] = list(pred_map.values())

        base_class['referenced_by'] = list(referenced_by_map.values())

        merged_classes.append(base_class)

    # Add all non-merged classes
    for cls in classes:
        if cls['class_uri'] not in merged_class_uris:
            merged_classes.append(cls)

    return merged_classes


def sanitize_table_name(class_name: str) -> str:
    """
    Convert a class name to a valid PostgreSQL table name

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


def sanitize_column_name(predicate_short: str) -> str:
    """
    Convert a predicate to a valid PostgreSQL column name

    Example: 'prov:endedAtTime' -> 'ended_at_time'
             'cpmeta:hasObjectSpec' -> 'has_object_spec'
    """
    # Remove namespace prefix
    if ':' in predicate_short:
        namespace, name = predicate_short.split(':', 1)
    else:
        name = predicate_short.split('/')[-1].split('#')[-1]
        namespace = ''

    # Convert CamelCase to snake_case
    # Handle patterns like hasXYZ or isXYZ
    name = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', name)
    name = name.lower()

    # Clean up
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


def get_predicate_columns(class_data: dict, type_map: Dict[str, str],
                         min_coverage: float = 0,
                         exclude_namespaces: Set[str] = None) -> List[Dict]:
    """
    Get list of columns to create for a class based on its predicates

    Args:
        class_data: Class information from analysis JSON
        type_map: Dict mapping predicate URIs to PostgreSQL types
        min_coverage: Minimum coverage percentage to include predicate
        exclude_namespaces: Set of namespace prefixes to exclude

    Returns: List of dicts with keys: name, type, predicate_uri, coverage
    """
    columns = []
    exclude_namespaces = exclude_namespaces or set()

    for pred_info in class_data.get('predicates', []):
        # Skip if below coverage threshold
        if pred_info['coverage_percentage'] < min_coverage:
            continue

        # Skip excluded namespaces
        if pred_info['namespace'] in exclude_namespaces:
            continue

        # Skip rdf:type as it's redundant (we know the class)
        if pred_info['predicate_short'] == 'rdf:type':
            continue

        col_name = sanitize_column_name(pred_info['predicate_short'])

        # Look up type from type_map, default to TEXT if not found
        col_type = type_map.get(pred_info['predicate_uri'], 'TEXT')

        columns.append({
            'name': col_name,
            'type': col_type,
            'predicate_uri': pred_info['predicate_uri'],
            'predicate_short': pred_info['predicate_short'],
            'coverage': pred_info['coverage_percentage']
        })

    return columns


def build_foreign_key_map(classes: List[dict]) -> Dict[str, List[Tuple[str, str, str]]]:
    """
    Build a map of which columns are foreign keys to other tables

    Deduplicates foreign keys so each column only references one table
    (chooses the most common target table for polymorphic references)

    Returns: Dict[table_name] -> List[(column_name, ref_table, predicate_uri)]
    """
    # First, build a map of class_uri -> table_name
    class_to_table = {}
    for class_data in classes:
        table_name = sanitize_table_name(class_data['class_name'])
        class_to_table[class_data['class_uri']] = table_name

    # Build FK map with counts to track polymorphic references
    # Structure: table_name -> column_name -> ref_table -> (predicate_uri, count)
    fk_candidates = defaultdict(lambda: defaultdict(lambda: defaultdict(lambda: {'predicate_uri': None, 'count': 0})))

    # For each class, look at its references_to
    for class_data in classes:
        table_name = sanitize_table_name(class_data['class_name'])

        for ref in class_data.get('references_to', []):
            ref_class_uri = ref['class_uri']

            # Skip if referenced class isn't in our table set
            if ref_class_uri not in class_to_table:
                continue

            ref_table = class_to_table[ref_class_uri]

            # For each predicate that creates this reference
            for pred in ref['predicates']:
                col_name = sanitize_column_name(pred['predicate_short'])
                count = pred.get('count', 1)

                # Track this FK candidate with its count
                if fk_candidates[table_name][col_name][ref_table]['predicate_uri'] is None:
                    fk_candidates[table_name][col_name][ref_table]['predicate_uri'] = pred['predicate_uri']
                    fk_candidates[table_name][col_name][ref_table]['count'] = count
                else:
                    # Accumulate counts for this FK
                    fk_candidates[table_name][col_name][ref_table]['count'] += count

    # Now deduplicate: for each column, pick the target table with highest count
    fk_map = defaultdict(list)

    for table_name, columns in fk_candidates.items():
        for col_name, targets in columns.items():
            if len(targets) == 1:
                # Only one target - no duplication
                ref_table = list(targets.keys())[0]
                pred_uri = targets[ref_table]['predicate_uri']
                fk_map[table_name].append((col_name, ref_table, pred_uri))
            else:
                # Multiple targets - pick the one with highest count
                best_target = max(targets.items(), key=lambda x: x[1]['count'])
                ref_table = best_target[0]
                pred_uri = best_target[1]['predicate_uri']
                fk_map[table_name].append((col_name, ref_table, pred_uri))

                # Log the polymorphic relationship for reference
                other_targets = [t for t in targets.keys() if t != ref_table]
                print(f"  Note: Polymorphic FK {table_name}.{col_name} -> {ref_table} "
                      f"(also references: {', '.join(other_targets)})")

    return dict(fk_map)


def generate_create_table_sql(table_name: str, columns: List[Dict],
                              foreign_keys: List[Tuple[str, str, str]] = None,
                              merge_config: dict = None) -> str:
    """
    Generate CREATE TABLE statement

    Args:
        table_name: Name of the table
        columns: List of column definitions
        foreign_keys: List of (column_name, ref_table, predicate_uri) tuples
        merge_config: Merge configuration if this is a union table
    """
    fk_cols = {fk[0] for fk in (foreign_keys or [])}

    lines = [f"CREATE TABLE IF NOT EXISTS {table_name} ("]
    lines.append("    id TEXT PRIMARY KEY,")

    # Add entity_type discriminator column for merged tables
    if merge_config:
        type_column = merge_config.get('type_column', 'entity_type')
        type_values = list(merge_config.get('type_values', {}).values())
        type_check = ', '.join(f"'{v}'" for v in type_values)
        lines.append(f"    {type_column} TEXT NOT NULL CHECK ({type_column} IN ({type_check})),")

    # Add regular columns
    for col in columns:
        # If this column is a foreign key, ensure it's TEXT type
        col_type = 'TEXT' if col['name'] in fk_cols else col['type']
        lines.append(f"    {col['name']} {col_type},")

    # Remove trailing comma from last column
    if lines[-1].endswith(','):
        lines[-1] = lines[-1][:-1]

    lines.append(");")

    return '\n'.join(lines)


def generate_foreign_key_sql(table_name: str, foreign_keys: List[Tuple[str, str, str]]) -> str:
    """
    Generate ALTER TABLE statements to add foreign key constraints
    """
    if not foreign_keys:
        return ""

    lines = []
    for col_name, ref_table, predicate_uri in foreign_keys:
        fk_name = f"fk_{table_name}_{col_name}"
        lines.append(
            f"ALTER TABLE {table_name} ADD CONSTRAINT {fk_name} "
            f"FOREIGN KEY ({col_name}) REFERENCES {ref_table}(id);"
        )

    return '\n'.join(lines)


def generate_indexes_sql(table_name: str, columns: List[Dict],
                        foreign_keys: List[Tuple[str, str, str]] = None) -> str:
    """
    Generate CREATE INDEX statements for performance
    """
    lines = []
    fk_cols = {fk[0] for fk in (foreign_keys or [])}

    # Index foreign key columns
    for col_name in fk_cols:
        idx_name = f"idx_{table_name}_{col_name}"
        lines.append(f"CREATE INDEX IF NOT EXISTS {idx_name} ON {table_name}({col_name});")

    # Index commonly queried columns (timestamps, high-coverage columns)
    for col in columns:
        if col['name'] in fk_cols:
            continue  # Already indexed

        # Index if high coverage or timestamp type
        if col['coverage'] >= 90 or 'TIMESTAMP' in col['type']:
            idx_name = f"idx_{table_name}_{col['name']}"
            lines.append(f"CREATE INDEX IF NOT EXISTS {idx_name} ON {table_name}({col['name']});")

    return '\n'.join(lines)


def generate_insert_sql(table_name: str, class_uri: str, columns: List[Dict],
                       rdf_type_uri: str, triples_table: str = 'rdf_triples',
                       merge_config: dict = None, merged_from: List[str] = None,
                       class_uri_map: Dict[str, str] = None) -> str:
    """
    Generate INSERT statement to populate table from rdf_triples

    Uses conditional aggregation (similar to PIVOT) to transform triples into rows.
    For merged tables, generates UNION of multiple queries (one per merged class).
    """
    if not merge_config or not merged_from:
        # Simple non-merged table
        return _generate_single_insert_sql(table_name, class_uri, columns,
                                          rdf_type_uri, triples_table)

    # Merged table - generate UNION of inserts for each merged class
    type_column = merge_config.get('type_column', 'entity_type')
    type_values = merge_config.get('type_values', {})

    # Build a UNION query for all merged classes
    union_parts = []

    for class_name in merged_from:
        if class_name not in class_uri_map:
            continue

        cls_uri = class_uri_map[class_name]
        type_value = type_values.get(class_name, 'unknown')

        # Build SELECT for this class
        select_lines = ["SELECT"]
        select_lines.append("    subj AS id")
        select_lines.append(f"    , '{type_value}' AS {type_column}")

        # For each column, use appropriate aggregate function to pivot the predicate values
        # Use BOOL_OR for boolean columns, MAX for others
        for col in columns:
            pred_uri = col['predicate_uri']
            col_type = col['type']
            cast_expr = _get_cast_expression(col_type)
            agg_func = _get_aggregate_function(col_type)
            select_lines.append(f"    , {agg_func}(CASE WHEN pred = '{pred_uri}' THEN {cast_expr} ELSE NULL END) AS {col['name']}")

        select_lines.append(f"FROM {triples_table}")
        select_lines.append(f"WHERE subj IN (")
        select_lines.append(f"    SELECT subj FROM {triples_table}")
        select_lines.append(f"    WHERE pred = '{rdf_type_uri}' AND obj = '{cls_uri}'")
        select_lines.append(")")
        select_lines.append("GROUP BY subj")

        union_parts.append('\n'.join(select_lines))

    # Combine with UNION ALL
    lines = [f"INSERT INTO {table_name} (id, {type_column}"]
    for col in columns:
        lines.append(f", {col['name']}")
    lines.append(")")
    lines.append('\nUNION ALL\n'.join(union_parts))
    lines.append(";")

    return '\n'.join(lines)


def _generate_single_insert_sql(table_name: str, class_uri: str, columns: List[Dict],
                                rdf_type_uri: str, triples_table: str) -> str:
    """Generate INSERT statement for a single non-merged table"""
    lines = [f"INSERT INTO {table_name} (id"]

    # Add column names
    for col in columns:
        lines.append(f", {col['name']}")

    lines.append(")")
    lines.append("SELECT")
    lines.append("    subj AS id")

    # For each column, use appropriate aggregate function to pivot the predicate values
    # Use BOOL_OR for boolean columns, MAX for others
    for col in columns:
        pred_uri = col['predicate_uri']
        col_type = col['type']
        cast_expr = _get_cast_expression(col_type)
        agg_func = _get_aggregate_function(col_type)
        lines.append(f"    , {agg_func}(CASE WHEN pred = '{pred_uri}' THEN {cast_expr} ELSE NULL END) AS {col['name']}")

    lines.append(f"FROM {triples_table}")
    lines.append(f"WHERE subj IN (")
    lines.append(f"    SELECT subj FROM {triples_table}")
    lines.append(f"    WHERE pred = '{rdf_type_uri}' AND obj = '{class_uri}'")
    lines.append(")")
    lines.append("GROUP BY subj;")

    return '\n'.join(lines)


def _get_cast_expression(col_type: str) -> str:
    """Get the appropriate CAST expression for a column type"""
    if col_type == 'TIMESTAMP WITH TIME ZONE':
        return "obj::TIMESTAMP WITH TIME ZONE"
    elif col_type == 'DATE':
        return "obj::DATE"
    elif col_type == 'INTEGER':
        return "obj::INTEGER"
    elif col_type == 'BIGINT':
        return "obj::BIGINT"
    elif col_type == 'SMALLINT':
        return "obj::SMALLINT"
    elif col_type == 'DOUBLE PRECISION':
        return "obj::DOUBLE PRECISION"
    elif col_type == 'BOOLEAN':
        return "CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END"
    else:
        return "obj"


def _get_aggregate_function(col_type: str) -> str:
    """Get the appropriate aggregate function for a column type

    PostgreSQL doesn't have MAX() for boolean types, so we use BOOL_OR() instead.
    BOOL_OR returns TRUE if any value is TRUE, which is equivalent to MAX for booleans.
    """
    if col_type == 'BOOLEAN':
        return 'BOOL_OR'
    else:
        return 'MAX'


def print_summary(classes_data: List[dict], table_names: Dict[str, str],
                 columns_map: Dict[str, List[Dict]], fk_map: Dict[str, List]):
    """Print summary of tables to be created"""
    print("\n" + "=" * 80)
    print("TABLE GENERATION SUMMARY")
    print("=" * 80)
    print(f"Total classes: {len(classes_data)}")
    print(f"Total tables to create: {len(table_names)}")
    print("\nTables:")
    print("-" * 80)

    for class_data in classes_data:
        table_name = table_names[class_data['class_uri']]
        columns = columns_map.get(table_name, [])
        fks = fk_map.get(table_name, [])

        print(f"\n  {table_name}")
        print(f"    Class: {class_data['class_name']}")
        print(f"    Instances: {class_data['instance_count']:,}")
        print(f"    Columns: {len(columns)}")
        print(f"    Foreign Keys: {len(fks)}")

        if fks:
            print(f"    References:")
            for col_name, ref_table, _ in fks[:3]:
                print(f"      - {col_name} -> {ref_table}")
            if len(fks) > 3:
                print(f"      ... and {len(fks) - 3} more")

    print("\n" + "=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description='Generate database tables from class_predicates_analysis.json',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # First, run infer_predicate_types.py to generate predicate_types.json
  ./infer_predicate_types.py

  # Then generate SQL (creates four files: schema, population, FKs, and indexes)
  %(prog)s                                    # Generates create_class_tables.sql + populate_class_tables.sql + create_foreign_keys.sql + create_indexes.sql
  %(prog)s --min-coverage 50                  # Only include predicates with 50%+ coverage
  %(prog)s --exclude-namespaces rdf,rdfs      # Skip RDF/RDFS predicates
  %(prog)s --drop                             # Include DROP TABLE statements

  # Execute directly in database (runs both schema and population)
  %(prog)s --db --host localhost --dbname postgres
        """
    )

    parser.add_argument('--input', default='class_predicates_analysis.json',
                       help='Input JSON file')
    parser.add_argument('--types-json', default='predicate_types.json',
                       help='Predicate types JSON file ')
    parser.add_argument('--output', default='class_tables/create_class_tables.sql',
                       help='Output SQL file for schema ')
    parser.add_argument('--populate-output', default='class_tables/populate_class_tables.sql',
                       help='Output SQL file for population')
    parser.add_argument('--fk-output', default='class_tables/create_foreign_keys.sql',
                       help='Output SQL file for foreign keys')
    parser.add_argument('--index-output', default='class_tables/create_indexes.sql',
                       help='Output SQL file for indexes')
    parser.add_argument('--min-coverage', type=float, default=0,
                       help='Minimum coverage percentage for predicates (default: 0)')
    parser.add_argument('--exclude-namespaces', default='',
                       help='Comma-separated list of namespaces to exclude (e.g., rdf,rdfs)')
    parser.add_argument('--triples-table', default='rdf_triples',
                       help='Name of the triples table (default: rdf_triples)')
    parser.add_argument('--db', help='Execute SQL directly in database (requires connection params)')
    parser.add_argument('--host', default='localhost', help='Database host (for --db)')
    parser.add_argument('--port', type=int, default=5432, help='Database port (for --db)')
    parser.add_argument('--user', default='postgres', help='Database user (for --db)')
    parser.add_argument('--dbname', default='postgres', help='Database name (for --db)')
    parser.add_argument('--password', default='ontop', help='Database password (for --db)')

    args = parser.parse_args()

    # Parse excluded namespaces
    exclude_namespaces = set(ns.strip() for ns in args.exclude_namespaces.split(',') if ns.strip())

    # Load analysis JSON
    print(f"Loading {args.input}...")
    data = load_analysis_json(args.input)

    classes = data.get('classes', [])
    print(f"Found {len(classes)} classes")

    if not classes:
        print("No classes found in JSON!")
        return 1

    # Build class URI map for merging
    print("\nBuilding class URI map...")
    class_uri_map = build_class_uri_map(classes)

    # Merge classes according to MERGE_GROUPS configuration
    print("Merging classes for union tables...")
    original_count = len(classes)
    classes = merge_classes(classes, class_uri_map)
    merged_count = original_count - len(classes) + sum(1 for c in classes if 'merge_config' in c)
    if merged_count > 0:
        print(f"  Merged {merged_count} classes into {sum(1 for c in classes if 'merge_config' in c)} union tables")
        print(f"  Total tables after merging: {len(classes)}")

    # Load predicate types
    print(f"\nLoading predicate types from {args.types_json}...")
    try:
        type_map = load_predicate_types(args.types_json)
        print(f"Loaded types for {len(type_map)} predicates")
    except FileNotFoundError:
        print(f"Error: {args.types_json} not found!")
        print(f"Run infer_predicate_types.py first to generate predicate types.")
        return 1
    except Exception as e:
        print(f"Error loading predicate types: {e}")
        return 1

    try:
        rdf_type_uri = f"{NS['rdf']}type"

        # Build table name map
        table_names = {}
        for class_data in classes:
            table_name = sanitize_table_name(class_data['class_name'])
            table_names[class_data['class_uri']] = table_name

        # Build columns for each table
        print("\nBuilding table schemas...")
        columns_map = {}
        for i, class_data in enumerate(classes, 1):
            table_name = table_names[class_data['class_uri']]
            print(f"  [{i}/{len(classes)}] {table_name}...", end='\r')

            columns = get_predicate_columns(
                class_data, type_map,
                args.min_coverage, exclude_namespaces
            )
            columns_map[table_name] = columns

        print(f"\n  Built schemas for {len(classes)} classes")

        # Build foreign key map
        print("\nBuilding foreign key relationships...")
        fk_map = build_foreign_key_map(classes)
        print(f"  Found {sum(len(fks) for fks in fk_map.values())} foreign key relationships")

        # Print summary
        print_summary(classes, table_names, columns_map, fk_map)

        # Generate Schema SQL
        print(f"\nGenerating schema SQL...")
        schema_lines = []

        # Header
        schema_lines.append("-- Generated SQL for class-based tables (SCHEMA)")
        schema_lines.append(f"-- Source: {args.input}")
        schema_lines.append(f"-- Total tables: {len(table_names)}")
        schema_lines.append("")

        schema_lines.append("-- Drop existing tables")
        for table_name in sorted(table_names.values()):
            schema_lines.append(f"DROP TABLE IF EXISTS {table_name} CASCADE;")
        schema_lines.append("")

        # CREATE TABLE statements
        schema_lines.append("-- " + "=" * 70)
        schema_lines.append("-- CREATE TABLES")
        schema_lines.append("-- " + "=" * 70)
        schema_lines.append("")

        for class_data in classes:
            table_name = table_names[class_data['class_uri']]
            columns = columns_map[table_name]
            fks = fk_map.get(table_name, [])
            merge_config = class_data.get('merge_config')

            schema_lines.append(f"-- Table: {table_name}")
            if merge_config:
                merged_from = class_data.get('merged_from', [])
                schema_lines.append(f"-- UNION TABLE merging: {', '.join(merged_from)}")
            schema_lines.append(f"-- Class: {class_data['class_name']} ({class_data['instance_count']:,} instances)")
            schema_lines.append("")

            create_sql = generate_create_table_sql(table_name, columns, fks, merge_config)
            schema_lines.append(create_sql)
            schema_lines.append("")

        # Generate Foreign Key SQL
        print(f"Generating foreign key SQL...")
        fk_lines = []

        # Header
        fk_lines.append("-- Generated SQL for foreign key constraints")
        fk_lines.append(f"-- Source: {args.input}")
        fk_lines.append(f"-- Total tables: {len(table_names)}")
        fk_lines.append("")
        fk_lines.append("-- " + "=" * 70)
        fk_lines.append("-- FOREIGN KEY CONSTRAINTS")
        fk_lines.append("-- " + "=" * 70)
        fk_lines.append("")

        for table_name, fks in sorted(fk_map.items()):
            if fks:
                fk_sql = generate_foreign_key_sql(table_name, fks)
                fk_lines.append(f"-- Foreign keys for {table_name}")
                fk_lines.append(fk_sql)
                fk_lines.append("")

        # Generate Index SQL
        print(f"Generating index SQL...")
        index_lines = []

        # Header
        index_lines.append("-- Generated SQL for indexes")
        index_lines.append(f"-- Source: {args.input}")
        index_lines.append(f"-- Total tables: {len(table_names)}")
        index_lines.append("")
        index_lines.append("-- " + "=" * 70)
        index_lines.append("-- INDEXES")
        index_lines.append("-- " + "=" * 70)
        index_lines.append("")

        for class_data in classes:
            table_name = table_names[class_data['class_uri']]
            columns = columns_map[table_name]
            fks = fk_map.get(table_name, [])

            idx_sql = generate_indexes_sql(table_name, columns, fks)
            if idx_sql:
                index_lines.append(f"-- Indexes for {table_name}")
                index_lines.append(idx_sql)
                index_lines.append("")

        # Generate Population SQL
        print(f"Generating population SQL...")
        populate_lines = []

        # Header
        populate_lines.append("-- Generated SQL for class-based tables (POPULATION)")
        populate_lines.append(f"-- Source: {args.input}")
        populate_lines.append(f"-- Triples table: {args.triples_table}")
        populate_lines.append(f"-- Total tables: {len(table_names)}")
        populate_lines.append("")
        populate_lines.append("-- Run this script after creating tables with create_class_tables.sql")
        populate_lines.append("")

        # INSERT statements
        populate_lines.append("-- " + "=" * 70)
        populate_lines.append("-- POPULATE TABLES")
        populate_lines.append("-- " + "=" * 70)
        populate_lines.append("")

        for class_data in classes:
            table_name = table_names[class_data['class_uri']]
            columns = columns_map[table_name]

            if not columns:
                continue

            merge_config = class_data.get('merge_config')
            merged_from = class_data.get('merged_from', [])

            populate_lines.append(f"-- Populate {table_name}")
            if merge_config:
                populate_lines.append(f"-- UNION TABLE merging: {', '.join(merged_from)}")
            populate_lines.append(f"-- Class: {class_data['class_name']} ({class_data['instance_count']:,} instances)")
            insert_sql = generate_insert_sql(
                table_name, class_data['class_uri'], columns,
                rdf_type_uri, args.triples_table,
                merge_config, merged_from, class_uri_map
            )
            populate_lines.append(insert_sql)
            populate_lines.append("")

        # Write schema file
        schema_sql = '\n'.join(schema_lines)
        print(f"\nWriting schema SQL to {args.output}...")
        with open(args.output, 'w') as f:
            f.write(schema_sql)
        print(f"Schema SQL written to {args.output}")

        # Write population file
        populate_sql = '\n'.join(populate_lines)
        print(f"Writing population SQL to {args.populate_output}...")
        with open(args.populate_output, 'w') as f:
            f.write(populate_sql)
        print(f"Population SQL written to {args.populate_output}")

        # Write foreign key file
        fk_sql = '\n'.join(fk_lines)
        print(f"Writing foreign key SQL to {args.fk_output}...")
        with open(args.fk_output, 'w') as f:
            f.write(fk_sql)
        print(f"Foreign key SQL written to {args.fk_output}")

        # Write index file
        index_sql = '\n'.join(index_lines)
        print(f"Writing index SQL to {args.index_output}...")
        with open(args.index_output, 'w') as f:
            f.write(index_sql)
        print(f"Index SQL written to {args.index_output}")

        print(f"\nSchema lines: {len(schema_lines)}")
        print(f"Population lines: {len(populate_lines)}")
        print(f"Foreign key lines: {len(fk_lines)}")
        print(f"Index lines: {len(index_lines)}")

        # Execute if requested
        if args.db or '--db' in sys.argv:
            print("\n" + "=" * 80)
            print("EXECUTING SQL IN DATABASE")
            print("=" * 80)

            response = input("WARNING: --drop specified. This will DELETE existing tables. Continue? (yes/no): ")
            if response.lower() != 'yes':
                print("Aborted.")
                return 0

            # Connect to database for execution
            print(f"Connecting to database at {args.host}:{args.port}...")
            try:
                conn = psycopg2.connect(
                    host=args.host,
                    port=args.port,
                    user=args.user,
                    dbname=args.dbname,
                    password=args.password
                )
                cursor = conn.cursor()
            except Exception as e:
                print(f"Error connecting to database: {e}")
                return 1

            try:
                # Execute schema SQL
                print("\nExecuting schema SQL...")
                schema_statements = [s.strip() for s in schema_sql.split(';') if s.strip() and not s.strip().startswith('--')]
                print(f"  {len(schema_statements)} statements to execute")

                for i, statement in enumerate(schema_statements, 1):
                    if statement:
                        print(f"  [{i}/{len(schema_statements)}] Executing...", end='\r')
                        cursor.execute(statement)

                print(f"\n  Schema created successfully!")
                conn.commit()

                # Execute population SQL
                print("\nExecuting population SQL...")
                populate_statements = [s.strip() for s in populate_sql.split(';') if s.strip() and not s.strip().startswith('--')]
                print(f"  {len(populate_statements)} statements to execute")

                for i, statement in enumerate(populate_statements, 1):
                    if statement:
                        print(f"  [{i}/{len(populate_statements)}] Executing...", end='\r')
                        cursor.execute(statement)

                print(f"\n  Tables populated successfully!")
                conn.commit()

                # Execute foreign key SQL
                print("\nExecuting foreign key SQL...")
                fk_statements = [s.strip() for s in fk_sql.split(';') if s.strip() and not s.strip().startswith('--')]
                print(f"  {len(fk_statements)} statements to execute")

                for i, statement in enumerate(fk_statements, 1):
                    if statement:
                        print(f"  [{i}/{len(fk_statements)}] Executing...", end='\r')
                        cursor.execute(statement)

                print(f"\n  Foreign keys created successfully!")
                conn.commit()

                # Execute index SQL
                print("\nExecuting index SQL...")
                index_statements = [s.strip() for s in index_sql.split(';') if s.strip() and not s.strip().startswith('--')]
                print(f"  {len(index_statements)} statements to execute")

                for i, statement in enumerate(index_statements, 1):
                    if statement:
                        print(f"  [{i}/{len(index_statements)}] Executing...", end='\r')
                        cursor.execute(statement)

                print(f"\n  Indexes created successfully!")
                conn.commit()

                print("\nDone! All tables created, populated, with foreign keys and indexes!")

            except Exception as e:
                print(f"\nError executing SQL: {e}")
                conn.rollback()
                return 1
            finally:
                cursor.close()
                conn.close()

        print("\n✓ Complete!")

    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        return 1

    return 0


if __name__ == '__main__':
    sys.exit(main())
