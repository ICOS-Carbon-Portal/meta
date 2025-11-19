#!/usr/bin/env python3
"""
Fix Missing Foreign Key References (v2)

Identifies missing FK references WITHOUT relying on existing FK constraints.
Determines correct prefix and type by querying the rdf_triples table.

This script:
1. Reads class_predicates_analysis.json to determine FK relationships (same as generate_class_tables.py)
2. Queries database to find IDs that exist in source tables but not in target tables
3. Looks up full URIs for missing IDs in the rdf_triples table
4. Extracts prefix from the full URI
5. Determines type discriminator for merged tables
6. Generates INSERT statements to create stub rows with minimal data (id, rdf_subject, prefix, type)
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


# Table merge configuration - must match generate_class_tables.py
MERGE_GROUPS = {
    'ct_object_specs': {
        'classes': ['cpmeta:SimpleObjectSpec', 'cpmeta:DataObjectSpec'],
        'type_column': 'spec_type',
        'type_values': {
            'cpmeta:SimpleObjectSpec': 'simple',
            'cpmeta:DataObjectSpec': 'data'
        },
        'default_type': 'simple'
    },
    'ct_spatial_coverages': {
        'classes': ['cpmeta:SpatialCoverage', 'cpmeta:LatLonBox', 'cpmeta:Position'],
        'type_column': 'coverage_type',
        'type_values': {
            'cpmeta:SpatialCoverage': 'spatial',
            'cpmeta:LatLonBox': 'latlon',
            'cpmeta:Position': 'position'
        },
        'default_type': 'spatial'
    },
    'ct_organizations': {
        'classes': ['cpmeta:Organization', 'cpmeta:TC', 'cpmeta:Facility'],
        'type_column': 'org_type',
        'type_values': {
            'cpmeta:Organization': 'organization',
            'cpmeta:TC': 'thematic_center',
            'cpmeta:Facility': 'central_facility'
        },
        'default_type': 'organization'
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
        },
        'default_type': 'station'
    },
    'ct_dataset_specs': {
        'classes': ['cpmeta:DatasetSpec', 'cpmeta:TabularDatasetSpec'],
        'type_column': 'dataset_type',
        'type_values': {
            'cpmeta:DatasetSpec': 'dataset',
            'cpmeta:TabularDatasetSpec': 'tabular'
        },
        'default_type': 'dataset'
    }
}


def sanitize_table_name(class_name: str) -> str:
    """
    Convert a class name to a valid PostgreSQL table name
    (copied from generate_class_tables.py)
    """
    if class_name.startswith('MERGED:'):
        return class_name.replace('MERGED:', '')

    if ':' in class_name:
        namespace, name = class_name.split(':', 1)
    else:
        name = class_name.split('/')[-1].split('#')[-1]
        namespace = ''

    name = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', name)
    name = name.lower()

    if not name.endswith('s'):
        if name.endswith('y') and len(name) > 2 and name[-2] not in 'aeiou':
            name = name[:-1] + 'ies'
        elif name.endswith(('s', 'x', 'z', 'ch', 'sh')):
            name = name + 'es'
        else:
            name = name + 's'

    if namespace and namespace != 'cpmeta':
        name = f"{namespace}_{name}"

    name = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    name = re.sub(r'_+', '_', name)
    name = name.strip('_')

    if name and name[0].isdigit():
        name = f"t_{name}"

    reserved_words = {'user', 'table', 'select', 'insert', 'update', 'delete', 'from', 'where', 'group', 'order'}
    if name in reserved_words:
        name = f"tbl_{name}"

    return f"ct_{name}"


def sanitize_column_name(predicate_short: str) -> str:
    """
    Convert a predicate to a valid PostgreSQL column name
    (copied from generate_class_tables.py)
    """
    if ':' in predicate_short:
        namespace, name = predicate_short.split(':', 1)
    else:
        name = predicate_short.split('/')[-1].split('#')[-1]
        namespace = ''

    name = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', name)
    name = name.lower()

    name = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    name = re.sub(r'_+', '_', name)
    name = name.strip('_')

    if name and name[0].isdigit():
        name = f"col_{name}"

    reserved_words = {'user', 'table', 'select', 'insert', 'update', 'delete', 'from', 'where', 'group', 'order', 'type'}
    if name in reserved_words:
        name = f"{name}_value"

    return name


def build_foreign_key_map(classes: List[dict]) -> Dict[str, List[Tuple[str, str, str]]]:
    """
    Build a map of which columns are foreign keys to other tables
    (based on generate_class_tables.py but simplified)

    Returns: Dict[table_name] -> List[(column_name, ref_table, predicate_uri)]
    """
    # Build a map of class_uri -> table_name
    class_to_table = {}
    for class_data in classes:
        table_name = sanitize_table_name(class_data['class_name'])
        class_to_table[class_data['class_uri']] = table_name

    # Build FK map with counts to track polymorphic references
    fk_candidates = defaultdict(lambda: defaultdict(lambda: defaultdict(lambda: {'predicate_uri': None, 'count': 0})))

    # For each class, look at its references_to
    for class_data in classes:
        table_name = sanitize_table_name(class_data['class_name'])

        for ref in class_data.get('references_to', []):
            ref_class_uri = ref['class_uri']

            if ref_class_uri not in class_to_table:
                continue

            ref_table = class_to_table[ref_class_uri]

            for pred in ref['predicates']:
                col_name = sanitize_column_name(pred['predicate_short'])
                count = pred.get('count', 1)

                if fk_candidates[table_name][col_name][ref_table]['predicate_uri'] is None:
                    fk_candidates[table_name][col_name][ref_table]['predicate_uri'] = pred['predicate_uri']
                    fk_candidates[table_name][col_name][ref_table]['count'] = count
                else:
                    fk_candidates[table_name][col_name][ref_table]['count'] += count

    # Deduplicate: for each column, pick the target table with highest count
    fk_map = defaultdict(list)

    for table_name, columns in fk_candidates.items():
        for col_name, targets in columns.items():
            if len(targets) == 1:
                ref_table = list(targets.keys())[0]
                pred_uri = targets[ref_table]['predicate_uri']
                fk_map[table_name].append((col_name, ref_table, pred_uri))
            else:
                best_target = max(targets.items(), key=lambda x: x[1]['count'])
                ref_table = best_target[0]
                pred_uri = best_target[1]['predicate_uri']
                fk_map[table_name].append((col_name, ref_table, pred_uri))

    return dict(fk_map)


def load_analysis_json(json_path: str) -> dict:
    """Load and parse the class_predicates_analysis.json file"""
    with open(json_path, 'r') as f:
        return json.load(f)


def merge_classes(classes: List[dict], class_uri_map: Dict[str, str]) -> List[dict]:
    """
    Merge classes according to MERGE_GROUPS configuration
    (simplified version from generate_class_tables.py - we only need the table names)
    """
    class_to_union, union_configs = get_merge_mapping()

    uri_to_class = {c['class_uri']: c for c in classes}
    merged_class_uris = set()
    merged_classes = []

    for union_table, config in union_configs.items():
        classes_to_merge = []
        for class_name in config['classes']:
            if class_name in class_uri_map:
                class_uri = class_uri_map[class_name]
                if class_uri in uri_to_class:
                    classes_to_merge.append(uri_to_class[class_uri])
                    merged_class_uris.add(class_uri)

        if not classes_to_merge:
            continue

        base_class = classes_to_merge[0].copy()
        base_class['class_name'] = f"MERGED:{union_table}"
        base_class['merged_from'] = [c['class_name'] for c in classes_to_merge]
        base_class['merge_config'] = config

        # Merge references_to
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

                pred_map = {p['predicate_uri']: p for p in references_map[ref_class_uri]['predicates']}
                for pred in ref.get('predicates', []):
                    pred_uri = pred['predicate_uri']
                    if pred_uri not in pred_map:
                        pred_map[pred_uri] = pred.copy()
                    else:
                        pred_map[pred_uri]['count'] = pred_map[pred_uri].get('count', 0) + pred.get('count', 0)

                references_map[ref_class_uri]['predicates'] = list(pred_map.values())

        base_class['references_to'] = list(references_map.values())
        merged_classes.append(base_class)

    # Add all non-merged classes
    for cls in classes:
        if cls['class_uri'] not in merged_class_uris:
            merged_classes.append(cls)

    return merged_classes


def get_merge_mapping() -> Tuple[Dict[str, str], Dict[str, dict]]:
    """Build mappings for table merges"""
    class_to_union = {}
    union_configs = {}

    for union_table, config in MERGE_GROUPS.items():
        union_configs[union_table] = config
        for class_name in config['classes']:
            class_to_union[class_name] = union_table

    return class_to_union, union_configs


def build_class_uri_map(classes: List[dict]) -> Dict[str, str]:
    """Build a mapping from class short names to full URIs"""
    class_uri_map = {}
    for class_data in classes:
        class_name = class_data['class_name']
        class_uri = class_data['class_uri']
        class_uri_map[class_name] = class_uri
    return class_uri_map


def find_missing_refs(cursor, source_table: str, fk_column: str, target_table: str) -> Set[str]:
    """Find FK values in source_table.fk_column that don't exist in target_table.id"""
    query = f"""
        SELECT DISTINCT s.{fk_column}
        FROM {source_table} s
        LEFT JOIN {target_table} t ON s.{fk_column} = t.id
        WHERE s.{fk_column} IS NOT NULL
          AND t.id IS NULL
    """
    cursor.execute(query)
    return {row[0] for row in cursor.fetchall()}


def lookup_full_uri(cursor, missing_id: str, triples_table: str = 'rdf_triples') -> Optional[str]:
    """
    Query rdf_triples to find full URI for a missing ID

    Tries multiple strategies:
    1. Look for URIs ending with the missing ID in the object column
    2. Look for URIs ending with the missing ID in the subject column
    """
    # Strategy 1: Check object column (most common for FK values)
    query = sql.SQL("""
        SELECT DISTINCT obj
        FROM {}
        WHERE obj LIKE %s
          AND obj ~ %s
        LIMIT 1
    """).format(sql.Identifier(triples_table))

    pattern_like = f'%{missing_id}'
    pattern_regex = f'.*{re.escape(missing_id)}$'

    cursor.execute(query, (pattern_like, pattern_regex))
    result = cursor.fetchone()
    if result:
        return result[0]

    # Strategy 2: Check subject column
    query = sql.SQL("""
        SELECT DISTINCT subj
        FROM {}
        WHERE subj LIKE %s
          AND subj ~ %s
        LIMIT 1
    """).format(sql.Identifier(triples_table))

    cursor.execute(query, (pattern_like, pattern_regex))
    result = cursor.fetchone()
    if result:
        return result[0]

    return None


def split_uri(full_uri: str, id_suffix: str) -> Tuple[str, str]:
    """Extract prefix from full URI"""
    if not full_uri.endswith(id_suffix):
        raise ValueError(f"URI {full_uri} does not end with {id_suffix}")
    prefix = full_uri[:-len(id_suffix)]
    return prefix, id_suffix


def determine_type(cursor, full_uri: str, table_name: str, triples_table: str = 'rdf_triples') -> str:
    """
    For merged tables, determine the type discriminator value

    Queries rdf_triples for the rdf:type of the entity and maps it to the discriminator value
    """
    if table_name not in MERGE_GROUPS:
        return None

    config = MERGE_GROUPS[table_name]
    type_values = config['type_values']
    default_type = config.get('default_type', list(type_values.values())[0])

    # Query for rdf:type
    rdf_type_uri = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
    query = sql.SQL("""
        SELECT obj
        FROM {}
        WHERE subj = %s
          AND pred = %s
    """).format(sql.Identifier(triples_table))

    cursor.execute(query, (full_uri, rdf_type_uri))
    results = cursor.fetchall()

    if not results:
        return default_type

    # Try to match the RDF type to one of our configured types
    for row in results:
        rdf_class = row[0]

        # Convert full URI to short form (e.g., 'http://.../cpmeta/DataObjectSpec' -> 'cpmeta:DataObjectSpec')
        if '#' in rdf_class:
            namespace_uri, class_name = rdf_class.rsplit('#', 1)
            short_form = f"cpmeta:{class_name}"  # Assume cpmeta for now
        elif '/' in rdf_class:
            parts = rdf_class.rsplit('/', 1)
            if len(parts) == 2:
                class_name = parts[1]
                # Try to determine namespace
                if 'cpmeta' in rdf_class:
                    short_form = f"cpmeta:{class_name}"
                else:
                    short_form = class_name
            else:
                continue
        else:
            continue

        if short_form in type_values:
            return type_values[short_form]

    return default_type


def generate_stub_insert(target_table: str, missing_id: str, rdf_subject: str, prefix: str, type_val: Optional[str] = None) -> str:
    """Generate INSERT statement for stub row"""
    if type_val and target_table in MERGE_GROUPS:
        type_column = MERGE_GROUPS[target_table]['type_column']
        return f"INSERT INTO {target_table} (id, rdf_subject, prefix, {type_column}) VALUES ('{missing_id}', '{rdf_subject}', '{prefix}', '{type_val}') ON CONFLICT (id) DO NOTHING;"
    else:
        return f"INSERT INTO {target_table} (id, rdf_subject, prefix) VALUES ('{missing_id}', '{rdf_subject}', '{prefix}') ON CONFLICT (id) DO NOTHING;"


def main():
    parser = argparse.ArgumentParser(
        description='Fix missing foreign key references by creating stub rows',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Report missing references without fixing
  %(prog)s --report-only

  # Generate SQL file with INSERT statements
  %(prog)s --output fix_fk_violations.sql

  # Execute INSERT statements directly in database
  %(prog)s --execute

  # With custom database connection
  %(prog)s --execute --host localhost --dbname postgres --user postgres
        """
    )

    parser.add_argument('--input', default='class_predicates_analysis.json',
                       help='Input JSON file (default: class_predicates_analysis.json)')
    parser.add_argument('--triples-table', default='rdf_triples',
                       help='Name of the triples table (default: rdf_triples)')
    parser.add_argument('--report-only', action='store_true',
                       help='Only report missing references, do not fix')
    parser.add_argument('--output', type=str,
                       help='Output SQL file for INSERT statements')
    parser.add_argument('--execute', action='store_true',
                       help='Execute INSERT statements directly in database')

    # Database connection parameters
    parser.add_argument('--host', default='localhost', help='Database host (default: localhost)')
    parser.add_argument('--port', type=int, default=5432, help='Database port (default: 5432)')
    parser.add_argument('--user', default='postgres', help='Database user (default: postgres)')
    parser.add_argument('--dbname', default='postgres', help='Database name (default: postgres)')
    parser.add_argument('--password', default='ontop', help='Database password (default: ontop)')

    args = parser.parse_args()

    # Validate arguments
    if not args.report_only and not args.output and not args.execute:
        print("Error: Must specify --report-only, --output FILE, or --execute")
        return 1

    # Load analysis JSON
    print(f"Loading {args.input}...")
    data = load_analysis_json(args.input)
    classes = data.get('classes', [])
    print(f"Found {len(classes)} classes")

    if not classes:
        print("No classes found in JSON!")
        return 1

    # Build class URI map and merge classes
    print("Building class URI map...")
    class_uri_map = build_class_uri_map(classes)

    print("Merging classes for union tables...")
    classes = merge_classes(classes, class_uri_map)
    print(f"Total tables after merging: {len(classes)}")

    # Build foreign key map
    print("Building foreign key relationships from analysis JSON...")
    fk_map = build_foreign_key_map(classes)
    total_fks = sum(len(fks) for fks in fk_map.values())
    print(f"Found {total_fks} foreign key relationships")

    # Connect to database
    print(f"\nConnecting to database at {args.host}:{args.port}...")
    try:
        conn = psycopg2.connect(
            host=args.host,
            port=args.port,
            user=args.user,
            dbname=args.dbname,
            password=args.password
        )
        cursor = conn.cursor()
        print("Connected successfully")
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return 1

    try:
        # Find all missing references
        print("\n" + "=" * 80)
        print("SCANNING FOR MISSING FOREIGN KEY REFERENCES")
        print("=" * 80)

        all_missing = {}  # {(source_table, fk_column, target_table): set(missing_ids)}
        total_missing_count = 0

        for source_table, fks in sorted(fk_map.items()):
            for fk_column, target_table, pred_uri in fks:
                try:
                    missing = find_missing_refs(cursor, source_table, fk_column, target_table)
                    if missing:
                        all_missing[(source_table, fk_column, target_table)] = missing
                        total_missing_count += len(missing)
                        print(f"  {source_table}.{fk_column} -> {target_table}: {len(missing)} missing")
                        if len(missing) <= 10:
                            for mid in sorted(missing):
                                print(f"    - {mid}")
                        else:
                            for mid in sorted(list(missing)[:5]):
                                print(f"    - {mid}")
                            print(f"    ... and {len(missing) - 5} more")
                except Exception as e:
                    print(f"  Error checking {source_table}.{fk_column}: {e}")
                    continue

        print("\n" + "=" * 80)
        print(f"SUMMARY: Found {total_missing_count} missing references across {len(all_missing)} FK relationships")
        print("=" * 80)

        if total_missing_count == 0:
            print("\nNo missing references found! All foreign keys are valid.")
            return 0

        if args.report_only:
            print("\n--report-only specified, exiting without generating fixes")
            return 0

        # Generate INSERT statements
        print("\n" + "=" * 80)
        print("GENERATING INSERT STATEMENTS FOR MISSING REFERENCES")
        print("=" * 80)

        insert_statements = []
        failed_lookups = []

        for (source_table, fk_column, target_table), missing_ids in all_missing.items():
            print(f"\nProcessing {target_table} (referenced by {source_table}.{fk_column})...")

            for missing_id in sorted(missing_ids):
                # Lookup full URI
                full_uri = lookup_full_uri(cursor, missing_id, args.triples_table)

                if not full_uri:
                    failed_lookups.append((target_table, missing_id, "URI not found in rdf_triples"))
                    print(f"  WARNING: Could not find URI for {missing_id} in {args.triples_table}")
                    continue

                try:
                    # Extract prefix
                    prefix, id_part = split_uri(full_uri, missing_id)

                    # Determine type for merged tables
                    type_val = determine_type(cursor, full_uri, target_table, args.triples_table)

                    # Generate INSERT
                    insert_sql = generate_stub_insert(target_table, missing_id, full_uri, prefix, type_val)
                    insert_statements.append(insert_sql)

                except Exception as e:
                    failed_lookups.append((target_table, missing_id, str(e)))
                    print(f"  WARNING: Error processing {missing_id}: {e}")
                    continue

        print(f"\nGenerated {len(insert_statements)} INSERT statements")

        if failed_lookups:
            print(f"\nWARNING: {len(failed_lookups)} references could not be resolved:")
            for table, mid, reason in failed_lookups[:10]:
                print(f"  - {table}.{mid}: {reason}")
            if len(failed_lookups) > 10:
                print(f"  ... and {len(failed_lookups) - 10} more")

        # Output INSERT statements
        if args.output:
            print(f"\nWriting INSERT statements to {args.output}...")
            with open(args.output, 'w') as f:
                f.write("-- Generated SQL to fix missing foreign key references\n")
                f.write(f"-- Total INSERT statements: {len(insert_statements)}\n")
                f.write("-- Generated by fix_missing_fk_references.py\n\n")
                f.write("BEGIN;\n\n")
                for stmt in insert_statements:
                    f.write(stmt + "\n")
                f.write("\nCOMMIT;\n")
            print(f"SQL written to {args.output}")

        if args.execute:
            print("\n" + "=" * 80)
            print("EXECUTING INSERT STATEMENTS")
            print("=" * 80)

            response = input(f"\nAbout to insert {len(insert_statements)} stub rows. Continue? (yes/no): ")
            if response.lower() != 'yes':
                print("Aborted.")
                return 0

            print("Executing INSERTs...")
            for i, stmt in enumerate(insert_statements, 1):
                try:
                    cursor.execute(stmt)
                    if i % 100 == 0:
                        print(f"  [{i}/{len(insert_statements)}] Executed...")
                except Exception as e:
                    print(f"  Error executing statement {i}: {e}")
                    print(f"  Statement: {stmt}")
                    conn.rollback()
                    return 1

            conn.commit()
            print(f"\nSuccessfully inserted {len(insert_statements)} stub rows!")

        print("\n✓ Complete!")

    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        cursor.close()
        conn.close()

    return 0


if __name__ == '__main__':
    sys.exit(main())
