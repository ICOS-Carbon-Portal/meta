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
import duckdb

from generate_class_tables import MERGE_GROUPS


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


def load_prefix_analysis(json_path: str) -> Dict[str, Dict[str, int]]:
    """
    Load table prefix analysis from JSON

    Returns: Dict[table_name] -> Dict[prefix_uri] -> count
    """
    with open(json_path, 'r') as f:
        data = json.load(f)

    prefix_map = {}
    for table_name, table_info in data.get('tables', {}).items():
        prefix_map[table_name] = table_info.get('prefix_counts', {})

    return prefix_map


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


def get_array_columns(cursor) -> Set[Tuple[str, str]]:
    """
    Query database schema to identify array columns

    Returns: Set of (table_name, column_name) tuples for columns with array types
    """
    query = """
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name LIKE 'ct_%'
          AND data_type = 'ARRAY'
    """
    cursor.execute(query)
    return {(row[0], row[1]) for row in cursor.fetchall()}


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


def construct_uri_from_prefix(missing_id: str, prefix: str) -> Tuple[str, str]:
    """
    Directly construct URI from known prefix and ID

    Returns: (full_uri, prefix)
    """
    full_uri = prefix + missing_id
    return full_uri, prefix


def lookup_full_uri(cursor, missing_id: str, target_table: str, prefix_map: Dict[str, Dict[str, int]], triples_table: str = 'rdf_triples') -> Optional[Tuple[str, str, bool]]:
    """
    Get full URI and prefix for a missing ID

    Strategy:
    1. If target_table has exactly one prefix: construct URI directly (FAST)
    2. If target_table has multiple prefixes: query rdf_triples (SLOW but accurate)

    Returns: (full_uri, prefix, used_direct_construction) or None
    """
    # Check if we can use direct construction
    prefixes = prefix_map.get(target_table, {})

    if len(prefixes) == 1:
        # Single prefix - construct directly!
        prefix = list(prefixes.keys())[0]
        full_uri, prefix = construct_uri_from_prefix(missing_id, prefix)
        return (full_uri, prefix, True)

    elif len(prefixes) > 1:
        # Multiple prefixes - need to query rdf_triples to find the correct one
        # Try object column first (most common for FK values)
        query = sql.SQL("""
            SELECT DISTINCT obj
            FROM {}
            WHERE obj LIKE ?
              AND obj ~ ?
            LIMIT 1
        """).format(sql.Identifier(triples_table))

        pattern_like = f'%{missing_id}'
        pattern_regex = f'.*{re.escape(missing_id)}$'

        cursor.execute(query, (pattern_like, pattern_regex))
        result = cursor.fetchone()
        if result:
            full_uri = result[0]
            # Extract prefix
            if not full_uri.endswith(missing_id):
                return None
            prefix = full_uri[:-len(missing_id)]
            return (full_uri, prefix, False)

        # Try subject column
        query = sql.SQL("""
            SELECT DISTINCT subj
            FROM {}
            WHERE subj LIKE ?
              AND subj ~ ?
            LIMIT 1
        """).format(sql.Identifier(triples_table))

        cursor.execute(query, (pattern_like, pattern_regex))
        result = cursor.fetchone()
        if result:
            full_uri = result[0]
            if not full_uri.endswith(missing_id):
                return None
            prefix = full_uri[:-len(missing_id)]
            return (full_uri, prefix, False)

        return None

    else:
        # No known prefixes - query rdf_triples
        query = sql.SQL("""
            SELECT DISTINCT obj
            FROM {}
            WHERE obj LIKE ?
              AND obj ~ ?
            LIMIT 1
        """).format(sql.Identifier(triples_table))

        pattern_like = f'%{missing_id}'
        pattern_regex = f'.*{re.escape(missing_id)}$'

        cursor.execute(query, (pattern_like, pattern_regex))
        result = cursor.fetchone()
        if result:
            full_uri = result[0]
            if not full_uri.endswith(missing_id):
                return None
            prefix = full_uri[:-len(missing_id)]
            return (full_uri, prefix, False)

        query = sql.SQL("""
            SELECT DISTINCT subj
            FROM {}
            WHERE subj LIKE ?
              AND subj ~ ?
            LIMIT 1
        """).format(sql.Identifier(triples_table))

        cursor.execute(query, (pattern_like, pattern_regex))
        result = cursor.fetchone()
        if result:
            full_uri = result[0]
            if not full_uri.endswith(missing_id):
                return None
            prefix = full_uri[:-len(missing_id)]
            return (full_uri, prefix, False)

        return None


def determine_type(table_name: str) -> Optional[str]:
    """
    For merged tables, return the default type discriminator value

    No database query needed - just uses the default from MERGE_GROUPS config
    """
    if table_name not in MERGE_GROUPS:
        return None

    config = MERGE_GROUPS[table_name]
    default_type = config.get('default_type', list(config['type_values'].values())[0])
    return default_type


def escape_sql_string(s: str) -> str:
    """Escape single quotes for SQL string literals by doubling them"""
    return s.replace("'", "''")


def generate_stub_insert(target_table: str, missing_id: str, rdf_subject: str, prefix: str, type_val: Optional[str] = None) -> str:
    """Generate INSERT statement for stub row with proper SQL escaping"""
    # Escape all string values to prevent SQL injection and syntax errors
    escaped_id = escape_sql_string(missing_id)
    escaped_subject = escape_sql_string(rdf_subject)
    escaped_prefix = escape_sql_string(prefix)

    if type_val and target_table in MERGE_GROUPS:
        type_column = MERGE_GROUPS[target_table]['type_column']
        escaped_type = escape_sql_string(type_val)
        return f"INSERT INTO {target_table} (id, rdf_subject, prefix, {type_column}) VALUES ('{escaped_id}', '{escaped_subject}', '{escaped_prefix}', '{escaped_type}') ON CONFLICT (id) DO NOTHING;"
    else:
        return f"INSERT INTO {target_table} (id, rdf_subject, prefix) VALUES ('{escaped_id}', '{escaped_subject}', '{escaped_prefix}') ON CONFLICT (id) DO NOTHING;"


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

    # Load prefix analysis
    print("\nLoading prefix analysis from table_prefix_analysis.json...")
    try:
        prefix_map = load_prefix_analysis('table_prefix_analysis.json')
        print(f"Loaded prefix information for {len(prefix_map)} tables")

        # Count tables by prefix count
        single_prefix_tables = sum(1 for prefixes in prefix_map.values() if len(prefixes) == 1)
        multi_prefix_tables = sum(1 for prefixes in prefix_map.values() if len(prefixes) > 1)
        print(f"  - {single_prefix_tables} tables with single prefix (will use direct URI construction)")
        print(f"  - {multi_prefix_tables} tables with multiple prefixes (will query rdf_triples)")
    except FileNotFoundError:
        print(f"Error: table_prefix_analysis.json not found!")
        print(f"Prefix analysis file is required for constructing URIs.")
        return 1
    except Exception as e:
        print(f"Error loading prefix analysis: {e}")
        return 1

    # Build foreign key map
    print("\nBuilding foreign key relationships from analysis JSON...")
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
        # Identify array columns (arrays cannot have FK constraints in PostgreSQL)
        print("\nIdentifying array columns from database schema...")
        array_columns = get_array_columns(cursor)
        print(f"Found {len(array_columns)} array columns (will be skipped)")

        # Find all missing references
        print("\n" + "=" * 80)
        print("SCANNING FOR MISSING FOREIGN KEY REFERENCES")
        print("=" * 80)

        all_missing = {}  # {(source_table, fk_column, target_table): set(missing_ids)}
        total_missing_count = 0
        skipped_array_fks = 0

        for source_table, fks in sorted(fk_map.items()):
            for fk_column, target_table, pred_uri in fks:
                # Skip array columns - PostgreSQL doesn't support FK constraints on arrays
                if (source_table, fk_column) in array_columns:
                    skipped_array_fks += 1
                    print(f"  Skipping {source_table}.{fk_column} (array column)")
                    continue

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
        if skipped_array_fks > 0:
            print(f"Skipped {skipped_array_fks} array columns (PostgreSQL doesn't support FK constraints on arrays)")
        print("=" * 80)

        if total_missing_count == 0:
            print("\nNo missing references found! All foreign keys are valid.")
            return 0

        if args.report_only:
            print("\n--report-only specified, exiting without generating fixes")
            return 0

        # Deduplicate missing IDs by target table
        # Multiple FKs might reference the same missing ID
        print("\nDeduplicating missing IDs by target table...")
        missing_by_target = defaultdict(set)  # {target_table: set(missing_ids)}

        for (source_table, fk_column, target_table), missing_ids in all_missing.items():
            missing_by_target[target_table].update(missing_ids)

        total_unique_missing = sum(len(ids) for ids in missing_by_target.values())
        print(f"  {total_missing_count} total missing references")
        print(f"  {total_unique_missing} unique missing IDs to insert")
        if total_missing_count > total_unique_missing:
            print(f"  Eliminated {total_missing_count - total_unique_missing} duplicates")

        # Generate INSERT statements
        print("\n" + "=" * 80)
        print("GENERATING INSERT STATEMENTS FOR MISSING REFERENCES")
        print("=" * 80)

        insert_statements = []
        failed_lookups = []
        direct_construction_count = 0
        rdf_triples_lookup_count = 0

        for target_table in sorted(missing_by_target.keys()):
            missing_ids = missing_by_target[target_table]
            print(f"\nProcessing {target_table}: {len(missing_ids)} unique missing IDs")

            # Check if this table uses single prefix or multiple
            prefixes = prefix_map.get(target_table, {})
            if len(prefixes) == 1:
                print(f"  Using direct URI construction (single prefix)")
            elif len(prefixes) > 1:
                print(f"  Using rdf_triples lookup (multiple prefixes: {len(prefixes)})")
            else:
                print(f"  Using rdf_triples lookup (no known prefix)")

            for missing_id in sorted(missing_ids):
                # Lookup full URI
                uri_result = lookup_full_uri(cursor, missing_id, target_table, prefix_map, args.triples_table)

                if not uri_result:
                    failed_lookups.append((target_table, missing_id, "URI not found"))
                    print(f"  WARNING: Could not find URI for {missing_id}")
                    continue

                full_uri, prefix, used_direct = uri_result

                # Track statistics
                if used_direct:
                    direct_construction_count += 1
                else:
                    rdf_triples_lookup_count += 1

                try:
                    # Determine type for merged tables (always use default)
                    type_val = determine_type(target_table)

                    # Generate INSERT
                    insert_sql = generate_stub_insert(target_table, missing_id, full_uri, prefix, type_val)
                    insert_statements.append(insert_sql)

                except Exception as e:
                    failed_lookups.append((target_table, missing_id, str(e)))
                    print(f"  WARNING: Error processing {missing_id}: {e}")
                    continue

        print(f"\nGenerated {len(insert_statements)} INSERT statements")
        print(f"  - {direct_construction_count} using direct URI construction (fast)")
        print(f"  - {rdf_triples_lookup_count} using rdf_triples lookup (slower)")

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
