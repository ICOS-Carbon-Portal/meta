#!/usr/bin/env python3
"""
Infer PostgreSQL column types for RDF predicates by analyzing actual values

This script reads class_predicates_analysis.json, samples values from rdf_triples,
and infers appropriate PostgreSQL types for each predicate.

Results are saved to predicate_types.json for use by generate_class_tables.py
"""

import json
import re
import argparse
import sys
from collections import defaultdict
from typing import Dict, Set, List
from datetime import datetime
import duckdb


# Namespace definitions
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


def extract_all_predicates(data: dict) -> Dict[str, dict]:
    """
    Extract all unique predicates from the analysis JSON

    Returns: Dict[predicate_uri] -> {
        'predicate_short': str,
        'namespace': str,
        'classes_using': List[str]
    }
    """
    predicates = {}

    for class_data in data.get('classes', []):
        class_name = class_data['class_name']

        for pred_info in class_data.get('predicates', []):
            pred_uri = pred_info['predicate_uri']

            if pred_uri not in predicates:
                predicates[pred_uri] = {
                    'predicate_short': pred_info['predicate_short'],
                    'namespace': pred_info['namespace'],
                    'classes_using': []
                }

            predicates[pred_uri]['classes_using'].append(class_name)

    return predicates


def infer_column_type(cursor, predicate_uri: str, triples_table: str = 'rdf_triples',
                     sample_size: int = 100) -> Dict:
    """
    Infer PostgreSQL column type by sampling actual values from rdf_triples

    Returns: Dict with keys:
        - postgresql_type: str (e.g., 'TEXT', 'INTEGER', 'TIMESTAMP WITH TIME ZONE')
        - samples_analyzed: int
        - inference_details: dict with pattern match results
    """
    # Sample values for this predicate
    query = f"""
        SELECT obj
        FROM {triples_table}
        WHERE pred = ?
        LIMIT ?
    """

    cursor.execute(query, (predicate_uri, sample_size))
    samples = [row[0] for row in cursor.fetchall()]

    if not samples:
        return {
            'postgresql_type': 'TEXT',
            'samples_analyzed': 0,
            'inference_details': {
                'reason': 'no_samples_found'
            }
        }

    # Check if all values match a pattern
    all_integers = True
    all_floats = True
    all_timestamps = True
    all_dates = True
    all_booleans = True
    all_uris = True

    max_int_value = 0
    max_string_length = 0

    # Patterns
    timestamp_pattern = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}')
    date_pattern = re.compile(r'^\d{4}-\d{2}-\d{2}$')
    uri_pattern = re.compile(r'^https?://')

    for value in samples:
        if value is None:
            continue

        value_str = str(value).strip()
        max_string_length = max(max_string_length, len(value_str))

        # Check timestamp
        if not timestamp_pattern.match(value_str):
            all_timestamps = False

        # Check date
        if not date_pattern.match(value_str):
            all_dates = False

        # Check boolean
        if value_str.lower() not in ('true', 'false', '1', '0'):
            all_booleans = False

        # Check URI
        if not uri_pattern.match(value_str):
            all_uris = False

        # Check integer
        try:
            int_val = int(value_str)
            max_int_value = max(max_int_value, abs(int_val))
        except (ValueError, OverflowError):
            all_integers = False

        # Check float
        try:
            float(value_str)
        except (ValueError, OverflowError):
            all_floats = False

    # Determine type based on checks
    inference_details = {
        'all_integers': all_integers,
        'all_floats': all_floats,
        'all_timestamps': all_timestamps,
        'all_dates': all_dates,
        'all_booleans': all_booleans,
        'all_uris': all_uris,
        'max_int_value': max_int_value if all_integers else None,
        'max_string_length': max_string_length
    }

    if all_timestamps:
        postgresql_type = 'TIMESTAMP WITH TIME ZONE'
        inference_details['reason'] = 'all_samples_match_timestamp_pattern'
    elif all_dates:
        postgresql_type = 'DATE'
        inference_details['reason'] = 'all_samples_match_date_pattern'
    elif all_booleans:
        postgresql_type = 'BOOLEAN'
        inference_details['reason'] = 'all_samples_are_boolean_values'
    elif all_integers:
        # Choose appropriate integer type based on max value
        if max_int_value < 32767:
            postgresql_type = 'SMALLINT'
        elif max_int_value < 2147483647:
            postgresql_type = 'INTEGER'
        else:
            postgresql_type = 'BIGINT'
        inference_details['reason'] = 'all_samples_are_integers'
    elif all_floats:
        postgresql_type = 'DOUBLE PRECISION'
        inference_details['reason'] = 'all_samples_are_numeric'
    elif all_uris:
        postgresql_type = 'TEXT'
        inference_details['reason'] = 'all_samples_are_uris'
    else:
        postgresql_type = 'TEXT'
        inference_details['reason'] = 'mixed_or_text_values'

    return {
        'postgresql_type': postgresql_type,
        'samples_analyzed': len(samples),
        'inference_details': inference_details
    }


def main():
    parser = argparse.ArgumentParser(
        description='Infer PostgreSQL types for RDF predicates',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s                                        # Infer types and save to predicate_types.json
  %(prog)s --sample-size 500                      # Use larger sample for better accuracy
  %(prog)s --output my_types.json                 # Save to custom file
        """
    )

    parser.add_argument('--input', default='class_predicates_analysis.json',
                       help='Input JSON file (default: class_predicates_analysis.json)')
    parser.add_argument('--output', default='predicate_types.json',
                       help='Output JSON file (default: predicate_types.json)')
    parser.add_argument('--sample-size', type=int, default=100,
                       help='Number of values to sample per predicate (default: 100)')
    parser.add_argument('--triples-table', default='rdf_triples',
                       help='Name of the triples table (default: rdf_triples)')
    parser.add_argument('--host', default='localhost', help='Database host')
    parser.add_argument('--port', type=int, default=5432, help='Database port')
    parser.add_argument('--user', default='postgres', help='Database user')
    parser.add_argument('--dbname', default='postgres', help='Database name')
    parser.add_argument('--password', default='ontop', help='Database password')

    args = parser.parse_args()

    # Load analysis JSON
    print(f"Loading {args.input}...")
    data = load_analysis_json(args.input)

    # Extract all predicates
    print("Extracting predicates...")
    predicates = extract_all_predicates(data)
    print(f"Found {len(predicates)} unique predicates")

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
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return 1

    try:
        # Infer types for each predicate
        print(f"\nInferring types (sampling {args.sample_size} values per predicate)...")
        results = {
            'metadata': {
                'timestamp': datetime.now().isoformat(),
                'sample_size': args.sample_size,
                'triples_table': args.triples_table,
                'source_file': args.input,
                'total_predicates': len(predicates)
            },
            'types': {}
        }

        for i, (pred_uri, pred_info) in enumerate(sorted(predicates.items()), 1):
            print(f"  [{i}/{len(predicates)}] {pred_info['predicate_short']}...", end='\r')

            type_info = infer_column_type(cursor, pred_uri, args.triples_table, args.sample_size)

            results['types'][pred_uri] = {
                'postgresql_type': type_info['postgresql_type'],
                'predicate_short': pred_info['predicate_short'],
                'namespace': pred_info['namespace'],
                'samples_analyzed': type_info['samples_analyzed'],
                'classes_using': pred_info['classes_using'],
                'inference_details': type_info['inference_details']
            }

        print(f"\n  Analyzed {len(predicates)} predicates")

        # Print summary
        print("\n" + "=" * 80)
        print("TYPE INFERENCE SUMMARY")
        print("=" * 80)

        # Count by type
        type_counts = defaultdict(int)
        for pred_uri, type_info in results['types'].items():
            type_counts[type_info['postgresql_type']] += 1

        print("\nType distribution:")
        for pg_type, count in sorted(type_counts.items(), key=lambda x: -x[1]):
            print(f"  {pg_type:30} {count:4} predicates")

        # Write output
        print(f"\nWriting results to {args.output}...")
        with open(args.output, 'w') as f:
            json.dump(results, f, indent=2)

        print(f"Results saved to {args.output}")
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
