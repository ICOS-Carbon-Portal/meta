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


def sanitize_table_name(class_name: str) -> str:
    """
    Convert a class name to a valid PostgreSQL table name

    Example: 'cpmeta:DataObject' -> 'ct_data_objects'
             'prov:Activity' -> 'ct_prov_activities'
    """
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

    Returns: Dict[table_name] -> List[(column_name, ref_table, predicate_uri)]
    """
    # First, build a map of class_uri -> table_name
    class_to_table = {}
    for class_data in classes:
        table_name = sanitize_table_name(class_data['class_name'])
        class_to_table[class_data['class_uri']] = table_name

    fk_map = defaultdict(list)

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
                fk_map[table_name].append((col_name, ref_table, pred['predicate_uri']))

    return dict(fk_map)


def generate_create_table_sql(table_name: str, columns: List[Dict],
                              foreign_keys: List[Tuple[str, str, str]] = None) -> str:
    """
    Generate CREATE TABLE statement

    Args:
        table_name: Name of the table
        columns: List of column definitions
        foreign_keys: List of (column_name, ref_table, predicate_uri) tuples
    """
    fk_cols = {fk[0] for fk in (foreign_keys or [])}

    lines = [f"CREATE TABLE IF NOT EXISTS {table_name} ("]
    lines.append("    id TEXT PRIMARY KEY,")

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
                       rdf_type_uri: str, triples_table: str = 'rdf_triples') -> str:
    """
    Generate INSERT statement to populate table from rdf_triples

    Uses conditional aggregation (similar to PIVOT) to transform triples into rows.
    """
    lines = [f"INSERT INTO {table_name} (id"]

    # Add column names
    for col in columns:
        lines.append(f", {col['name']}")

    lines.append(")")
    lines.append("SELECT")
    lines.append("    subj AS id")

    # For each column, use MAX(CASE...) to pivot the predicate values
    for col in columns:
        pred_uri = col['predicate_uri']
        col_type = col['type']

        # Build CAST expression based on type
        cast_expr = "obj"
        if col_type == 'TIMESTAMP WITH TIME ZONE':
            cast_expr = "obj::TIMESTAMP WITH TIME ZONE"
        elif col_type == 'DATE':
            cast_expr = "obj::DATE"
        elif col_type == 'INTEGER':
            cast_expr = "obj::INTEGER"
        elif col_type == 'BIGINT':
            cast_expr = "obj::BIGINT"
        elif col_type == 'SMALLINT':
            cast_expr = "obj::SMALLINT"
        elif col_type == 'DOUBLE PRECISION':
            cast_expr = "obj::DOUBLE PRECISION"
        elif col_type == 'BOOLEAN':
            cast_expr = "CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END"

        lines.append(f"    , MAX(CASE WHEN pred = '{pred_uri}' THEN {cast_expr} ELSE NULL END) AS {col['name']}")

    lines.append(f"FROM {triples_table}")
    lines.append(f"WHERE subj IN (")
    lines.append(f"    SELECT subj FROM {triples_table}")
    lines.append(f"    WHERE pred = '{rdf_type_uri}' AND obj = '{class_uri}'")
    lines.append(")")
    lines.append("GROUP BY subj;")

    return '\n'.join(lines)


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

  # Then generate SQL (creates two files: schema and population)
  %(prog)s                                    # Generates create_class_tables.sql + populate_class_tables.sql
  %(prog)s --min-coverage 50                  # Only include predicates with 50%+ coverage
  %(prog)s --exclude-namespaces rdf,rdfs      # Skip RDF/RDFS predicates
  %(prog)s --drop                             # Include DROP TABLE statements

  # Execute directly in database (runs both schema and population)
  %(prog)s --db --host localhost --dbname postgres
        """
    )

    parser.add_argument('--input', default='class_predicates_analysis.json',
                       help='Input JSON file (default: class_predicates_analysis.json)')
    parser.add_argument('--types-json', default='predicate_types.json',
                       help='Predicate types JSON file (default: predicate_types.json)')
    parser.add_argument('--output', default='create_class_tables.sql',
                       help='Output SQL file for schema (default: create_class_tables.sql)')
    parser.add_argument('--populate-output', default='populate_class_tables.sql',
                       help='Output SQL file for population (default: populate_class_tables.sql)')
    parser.add_argument('--min-coverage', type=float, default=0,
                       help='Minimum coverage percentage for predicates (default: 0)')
    parser.add_argument('--exclude-namespaces', default='',
                       help='Comma-separated list of namespaces to exclude (e.g., rdf,rdfs)')
    parser.add_argument('--drop', action='store_true',
                       help='Include DROP TABLE statements')
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

    # Load predicate types
    print(f"Loading predicate types from {args.types_json}...")
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

        # DROP statements if requested
        if args.drop:
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

            schema_lines.append(f"-- Table: {table_name}")
            schema_lines.append(f"-- Class: {class_data['class_name']} ({class_data['instance_count']:,} instances)")
            schema_lines.append("")

            create_sql = generate_create_table_sql(table_name, columns, fks)
            schema_lines.append(create_sql)
            schema_lines.append("")

        # FOREIGN KEY constraints
        schema_lines.append("-- " + "=" * 70)
        schema_lines.append("-- FOREIGN KEY CONSTRAINTS")
        schema_lines.append("-- " + "=" * 70)
        schema_lines.append("")

        for table_name, fks in sorted(fk_map.items()):
            if fks:
                fk_sql = generate_foreign_key_sql(table_name, fks)
                schema_lines.append(f"-- Foreign keys for {table_name}")
                schema_lines.append(fk_sql)
                schema_lines.append("")

        # INDEXES
        schema_lines.append("-- " + "=" * 70)
        schema_lines.append("-- INDEXES")
        schema_lines.append("-- " + "=" * 70)
        schema_lines.append("")

        for class_data in classes:
            table_name = table_names[class_data['class_uri']]
            columns = columns_map[table_name]
            fks = fk_map.get(table_name, [])

            idx_sql = generate_indexes_sql(table_name, columns, fks)
            if idx_sql:
                schema_lines.append(f"-- Indexes for {table_name}")
                schema_lines.append(idx_sql)
                schema_lines.append("")

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

            populate_lines.append(f"-- Populate {table_name}")
            populate_lines.append(f"-- Class: {class_data['class_name']} ({class_data['instance_count']:,} instances)")
            insert_sql = generate_insert_sql(
                table_name, class_data['class_uri'], columns,
                rdf_type_uri, args.triples_table
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

        print(f"\nSchema lines: {len(schema_lines)}")
        print(f"Population lines: {len(populate_lines)}")

        # Execute if requested
        if args.db or '--db' in sys.argv:
            print("\n" + "=" * 80)
            print("EXECUTING SQL IN DATABASE")
            print("=" * 80)

            if args.drop:
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

                print("\nDone! All tables created and populated successfully!")

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
