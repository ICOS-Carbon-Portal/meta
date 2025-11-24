#!/usr/bin/env python3
"""
Analyze cardinality (value counts) for RDF predicates by class

This script queries rdf_triples to compute how many values each predicate
actually has per subject, grouped by class. This helps distinguish properties
that are semantically multi-valued (non-functional in ontology) but actually
single-valued in practice.

Results are saved to predicate_cardinality.json for use by generate_class_tables.py
"""

import json
import argparse
import sys
from collections import defaultdict
from typing import Dict, Set, List, Optional
import psycopg2


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


def compute_cardinality_for_class(cursor, class_uri: str, predicate_uris: List[str],
                                   rdf_type_uri: str, triples_table: str = 'rdf_triples') -> Dict[str, Dict]:
    """
    Compute cardinality statistics for predicates used by a specific class

    Args:
        cursor: Database cursor
        class_uri: URI of the class to analyze
        predicate_uris: List of predicate URIs to analyze for this class
        rdf_type_uri: URI for rdf:type
        triples_table: Name of the triples table

    Returns:
        Dict[predicate_uri] -> {
            'subjects_count': int,
            'min_values': int,
            'max_values': int,
            'avg_values': float,
            'p95_values': float
        }
    """
    if not predicate_uris:
        return {}

    # Build query to compute cardinality statistics
    # 1. Find all subjects of this class
    # 2. For each subject+predicate, count values
    # 3. Aggregate statistics per predicate
    query = f"""
        WITH class_subjects AS (
            -- Find all subjects of this class
            SELECT DISTINCT subj
            FROM {triples_table}
            WHERE pred = %s AND obj = %s
        ),
        subject_predicate_counts AS (
            -- Count how many values each subject has for each predicate
            SELECT
                t.pred,
                t.subj,
                COUNT(*) as value_count
            FROM {triples_table} t
            INNER JOIN class_subjects cs ON t.subj = cs.subj
            WHERE t.pred = ANY(%s)
            GROUP BY t.pred, t.subj
        )
        -- Aggregate statistics per predicate
        SELECT
            pred,
            COUNT(DISTINCT subj) as subjects_count,
            MIN(value_count) as min_values,
            MAX(value_count) as max_values,
            AVG(value_count) as avg_values,
            PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value_count) as p95_values
        FROM subject_predicate_counts
        GROUP BY pred
        ORDER BY pred
    """

    cursor.execute(query, (rdf_type_uri, class_uri, predicate_uris))
    results = {}

    for row in cursor.fetchall():
        pred_uri, subjects_count, min_val, max_val, avg_val, p95_val = row
        results[pred_uri] = {
            'subjects_count': subjects_count,
            'min_values': min_val,
            'max_values': max_val,
            'avg_values': float(avg_val),
            'p95_values': float(p95_val)
        }

    return results


def analyze_all_classes(cursor, analysis_data: dict, rdf_type_uri: str,
                        triples_table: str = 'rdf_triples',
                        predicate_filter: Optional[Set[str]] = None) -> Dict:
    """
    Analyze cardinality for all classes in the analysis data

    Args:
        cursor: Database cursor
        analysis_data: Parsed class_predicates_analysis.json
        rdf_type_uri: URI for rdf:type
        triples_table: Name of the triples table
        predicate_filter: Optional set of predicate URIs to analyze (for optimization)

    Returns:
        Dict with structure:
        {
            'classes': {
                class_uri: {
                    'class_name': str,
                    'predicates': {
                        predicate_uri: {cardinality_stats}
                    }
                }
            }
        }
    """
    results = {'classes': {}}
    classes = analysis_data.get('classes', [])

    for i, class_data in enumerate(classes, 1):
        class_uri = class_data['class_uri']
        class_name = class_data['class_name']
        instance_count = class_data['instance_count']

        print(f"[{i}/{len(classes)}] Analyzing {class_name} ({instance_count:,} instances)...", end='\r')

        # Extract predicates for this class
        predicate_uris = []
        for pred_info in class_data.get('predicates', []):
            pred_uri = pred_info['predicate_uri']

            # Skip rdf:type
            if pred_info['predicate_short'] == 'rdf:type':
                continue

            # Apply filter if provided
            if predicate_filter and pred_uri not in predicate_filter:
                continue

            predicate_uris.append(pred_uri)

        if not predicate_uris:
            continue

        # Compute cardinality for this class
        try:
            cardinality_stats = compute_cardinality_for_class(
                cursor, class_uri, predicate_uris, rdf_type_uri, triples_table
            )

            results['classes'][class_uri] = {
                'class_name': class_name,
                'instance_count': instance_count,
                'predicates': cardinality_stats
            }
        except Exception as e:
            print(f"\nError analyzing {class_name}: {e}")
            continue

    print()  # New line after progress indicator
    return results


def print_summary(results: Dict):
    """Print summary statistics"""
    print("\n" + "=" * 80)
    print("CARDINALITY ANALYSIS SUMMARY")
    print("=" * 80)

    total_classes = len(results['classes'])
    total_predicates = sum(len(c['predicates']) for c in results['classes'].values())

    # Count multi-valued predicates
    multi_valued = 0
    single_valued = 0

    for class_data in results['classes'].values():
        for pred_uri, stats in class_data['predicates'].items():
            if stats['max_values'] > 1:
                multi_valued += 1
            else:
                single_valued += 1

    print(f"Classes analyzed: {total_classes}")
    print(f"Total (class, predicate) pairs: {total_predicates}")
    print(f"  Single-valued (max=1): {single_valued}")
    print(f"  Multi-valued (max>1): {multi_valued}")
    print(f"  Percentage multi-valued: {100.0 * multi_valued / total_predicates:.1f}%")

    # Show some examples of multi-valued predicates
    print("\nExamples of multi-valued predicates (max > 1):")
    examples = []
    for class_data in results['classes'].values():
        class_name = class_data['class_name']
        for pred_uri, stats in class_data['predicates'].items():
            if stats['max_values'] > 1:
                pred_short = pred_uri.split('/')[-1].split('#')[-1]
                examples.append((class_name, pred_short, stats['max_values'], stats['avg_values']))
                if len(examples) >= 10:
                    break
        if len(examples) >= 10:
            break

    for class_name, pred_short, max_val, avg_val in examples[:10]:
        print(f"  {class_name}.{pred_short}: max={max_val}, avg={avg_val:.1f}")

    print("=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description='Analyze cardinality of predicates by class in RDF data',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Analyze all predicates for all classes
  %(prog)s

  # Analyze with custom database connection
  %(prog)s --host localhost --dbname mydb --user myuser --password mypass

  # Only analyze specific predicates (for optimization)
  %(prog)s --predicates-filter non_functional_predicates.txt

  # Use custom input/output files
  %(prog)s --input my_analysis.json --output my_cardinality.json
        """
    )

    parser.add_argument('--input', default='class_predicates_analysis.json',
                       help='Input JSON file with class/predicate analysis')
    parser.add_argument('--output', default='predicate_cardinality.json',
                       help='Output JSON file for cardinality statistics')
    parser.add_argument('--triples-table', default='rdf_triples',
                       help='Name of the triples table (default: rdf_triples)')
    parser.add_argument('--host', default='localhost',
                       help='Database host (default: localhost)')
    parser.add_argument('--port', type=int, default=5432,
                       help='Database port (default: 5432)')
    parser.add_argument('--user', default='postgres',
                       help='Database user (default: postgres)')
    parser.add_argument('--dbname', default='postgres',
                       help='Database name (default: postgres)')
    parser.add_argument('--password', default='ontop',
                       help='Database password (default: ontop)')
    parser.add_argument('--predicates-filter', type=str,
                       help='Optional file with list of predicate URIs to analyze (one per line)')

    args = parser.parse_args()

    # Load class predicates analysis
    print(f"Loading class predicates analysis from {args.input}...")
    try:
        analysis_data = load_analysis_json(args.input)
        classes_count = len(analysis_data.get('classes', []))
        print(f"  Found {classes_count} classes")
    except FileNotFoundError:
        print(f"Error: {args.input} not found!")
        print("Run analyze_class_predicates.py first to generate this file.")
        return 1
    except Exception as e:
        print(f"Error loading {args.input}: {e}")
        return 1

    # Load predicate filter if provided
    predicate_filter = None
    if args.predicates_filter:
        print(f"Loading predicate filter from {args.predicates_filter}...")
        try:
            with open(args.predicates_filter, 'r') as f:
                predicate_filter = set(line.strip() for line in f if line.strip())
            print(f"  Filtering to {len(predicate_filter)} predicates")
        except FileNotFoundError:
            print(f"Warning: {args.predicates_filter} not found, analyzing all predicates")

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
        print("  Connected successfully")
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return 1

    try:
        rdf_type_uri = f"{NS['rdf']}type"

        # Analyze cardinality
        print("\nAnalyzing cardinality for all classes...")
        results = analyze_all_classes(
            cursor, analysis_data, rdf_type_uri,
            args.triples_table, predicate_filter
        )

        # Print summary
        print_summary(results)

        # Write results
        print(f"\nWriting results to {args.output}...")
        with open(args.output, 'w') as f:
            json.dump(results, f, indent=2)
        print(f"  Cardinality analysis written to {args.output}")

        print("\nDone!")

    except Exception as e:
        print(f"\nError during analysis: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        cursor.close()
        conn.close()

    return 0


if __name__ == '__main__':
    sys.exit(main())
