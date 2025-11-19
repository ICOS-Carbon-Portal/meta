#!/usr/bin/env python3
"""
Analyze which predicates are used with each OWL class in the RDF data.
This helps identify which properties should be included in class-based tables.
"""

import psycopg2
import argparse
import os
import sys
import json
from collections import defaultdict
from datetime import datetime

# Configuration
TRIPLES_TABLE = os.environ.get('TRIPLES_TABLE', 'rdf_triples')

# Namespace definitions
NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'xsd': 'http://www.w3.org/2001/XMLSchema#',
    'cpmeta': 'http://meta.icos-cp.eu/ontologies/cpmeta/',
    'prov': 'http://www.w3.org/ns/prov#',
    'purl': 'http://purl.org/dc/terms/',
    'dcat': 'http://www.w3.org/ns/dcat#',
    'skos': 'http://www.w3.org/2004/02/skos/core#',
    'wdcgg': 'http://meta.icos-cp.eu/resources/wdcgg/',
    'ssn': 'http://www.w3.org/ns/ssn/'
}


def get_connection(host='localhost', port=5432, user='postgres', dbname='postgres', password='ontop'):
    """Create and return a PostgreSQL database connection."""
    try:
        return psycopg2.connect(
            host=host,
            port=port,
            user=user,
            dbname=dbname,
            password=password
        )
    except psycopg2.Error as e:
        print(f"Error connecting to database: {e}")
        sys.exit(1)


def check_triples_table(cursor):
    """Check which triples table exists and return its name."""
    global TRIPLES_TABLE

    for table_name in ['rdf_triples', 'triples']:
        cursor.execute("""
            SELECT EXISTS (
                SELECT FROM information_schema.tables
                WHERE table_name = %s
            );
        """, (table_name,))

        if cursor.fetchone()[0]:
            TRIPLES_TABLE = table_name
            print(f"Using triples table: {TRIPLES_TABLE}\n")
            return TRIPLES_TABLE

    print("Error: No triples table found (tried 'rdf_triples' and 'triples')")
    sys.exit(1)


def shorten_uri(uri):
    """Shorten a URI using namespace prefixes."""
    for prefix, namespace in NS.items():
        if uri.startswith(namespace):
            return uri.replace(namespace, f'{prefix}:')
    return uri


def get_all_classes(cursor):
    """Get all OWL classes from the RDF data."""
    query = f"""
        SELECT DISTINCT obj
        FROM {TRIPLES_TABLE}
        WHERE pred = %s
        ORDER BY obj
    """

    cursor.execute(query, (f"{NS['rdf']}type",))
    classes = [row[0] for row in cursor.fetchall()]

    # Filter to cpmeta classes and important OWL classes
    filtered_classes = [
        c for c in classes
        if c.startswith(NS['cpmeta']) or c in [
            f"{NS['prov']}Activity",
            f"{NS['prov']}Entity",
        ]
    ]

    return sorted(filtered_classes)


def count_instances(cursor, class_uri):
    """Count how many instances of a class exist."""
    query = f"""
        SELECT COUNT(DISTINCT subj)
        FROM {TRIPLES_TABLE}
        WHERE pred = %s AND obj = %s
    """

    cursor.execute(query, (f"{NS['rdf']}type", class_uri))
    return cursor.fetchone()[0]


def get_predicates_for_class(cursor, class_uri):
    """Get all predicates used with instances of a specific class."""
    query = f"""
        SELECT DISTINCT t.pred, COUNT(*) as usage_count
        FROM {TRIPLES_TABLE} t
        WHERE t.subj IN (
            SELECT subj
            FROM {TRIPLES_TABLE}
            WHERE pred = %s AND obj = %s
        )
        GROUP BY t.pred
        ORDER BY t.pred
    """

    cursor.execute(query, (f"{NS['rdf']}type", class_uri))
    return [(row[0], row[1]) for row in cursor.fetchall()]


def analyze_predicate_values(cursor, class_uri, predicate):
    """Analyze the value distribution for a predicate."""
    query = f"""
        SELECT
            COUNT(*) as total_values,
            COUNT(DISTINCT obj) as distinct_values,
            MIN(LENGTH(obj)) as min_length,
            MAX(LENGTH(obj)) as max_length
        FROM {TRIPLES_TABLE} t
        WHERE t.pred = %s
          AND t.subj IN (
              SELECT subj
              FROM {TRIPLES_TABLE}
              WHERE pred = %s AND obj = %s
          )
    """

    cursor.execute(query, (predicate, f"{NS['rdf']}type", class_uri))
    return cursor.fetchone()


def get_class_references(cursor, class_uri):
    """Get classes that reference this class (incoming) and classes this class references (outgoing)."""
    rdf_type = f"{NS['rdf']}type"

    # Incoming references: Find classes whose instances point TO this class
    incoming_query = f"""
        SELECT referring_class.obj as class_uri, t.pred as predicate_uri, COUNT(*) as reference_count
        FROM {TRIPLES_TABLE} t
        -- t.obj are instances of the target class (class_uri)
        JOIN {TRIPLES_TABLE} class_instances ON class_instances.subj = t.obj
            AND class_instances.pred = %s AND class_instances.obj = %s
        -- Find what classes the subjects (t.subj) belong to
        JOIN {TRIPLES_TABLE} referring_class ON referring_class.subj = t.subj
            AND referring_class.pred = %s
        -- Exclude self-references and rdf:type predicates
        WHERE referring_class.obj != %s AND t.pred != %s
        GROUP BY referring_class.obj, t.pred
        ORDER BY referring_class.obj, reference_count DESC
    """

    cursor.execute(incoming_query, (rdf_type, class_uri, rdf_type, class_uri, rdf_type))

    # Process incoming results: group by class, collect predicates
    incoming_by_class = defaultdict(lambda: {"predicates": [], "total_count": 0})
    for row in cursor.fetchall():
        class_uri_key = row[0]
        predicate_uri = row[1]
        count = row[2]

        incoming_by_class[class_uri_key]["predicates"].append({
            "predicate_uri": predicate_uri,
            "predicate_short": shorten_uri(predicate_uri),
            "count": count
        })
        incoming_by_class[class_uri_key]["total_count"] += count

    incoming = [
        {
            "class_uri": class_uri_key,
            "class_name": shorten_uri(class_uri_key),
            "reference_count": data["total_count"],
            "predicates": data["predicates"]
        }
        for class_uri_key, data in incoming_by_class.items()
    ]
    incoming.sort(key=lambda x: x["reference_count"], reverse=True)

    # Outgoing references: Find classes that instances of this class point TO
    outgoing_query = f"""
        SELECT target_class.obj as class_uri, t.pred as predicate_uri, COUNT(*) as reference_count
        FROM {TRIPLES_TABLE} t
        -- t.subj are instances of the source class (class_uri)
        JOIN {TRIPLES_TABLE} class_instances ON class_instances.subj = t.subj
            AND class_instances.pred = %s AND class_instances.obj = %s
        -- Find what classes the objects (t.obj) belong to
        JOIN {TRIPLES_TABLE} target_class ON target_class.subj = t.obj
            AND target_class.pred = %s
        -- Exclude self-references and rdf:type predicates
        WHERE target_class.obj != %s AND t.pred != %s
        GROUP BY target_class.obj, t.pred
        ORDER BY target_class.obj, reference_count DESC
    """

    cursor.execute(outgoing_query, (rdf_type, class_uri, rdf_type, class_uri, rdf_type))

    # Process outgoing results: group by class, collect predicates
    outgoing_by_class = defaultdict(lambda: {"predicates": [], "total_count": 0})
    for row in cursor.fetchall():
        class_uri_key = row[0]
        predicate_uri = row[1]
        count = row[2]

        outgoing_by_class[class_uri_key]["predicates"].append({
            "predicate_uri": predicate_uri,
            "predicate_short": shorten_uri(predicate_uri),
            "count": count
        })
        outgoing_by_class[class_uri_key]["total_count"] += count

    outgoing = [
        {
            "class_uri": class_uri_key,
            "class_name": shorten_uri(class_uri_key),
            "reference_count": data["total_count"],
            "predicates": data["predicates"]
        }
        for class_uri_key, data in outgoing_by_class.items()
    ]
    outgoing.sort(key=lambda x: x["reference_count"], reverse=True)

    return incoming, outgoing


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Analyze predicate usage for all OWL classes in RDF data",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s                              # Analyze all classes
  %(prog)s --class DataObjectSpec       # Analyze specific class
  %(prog)s --min-instances 100          # Only classes with 100+ instances
  %(prog)s --detailed                   # Include value statistics
        """
    )

    parser.add_argument('--host', default='localhost', help='Database host')
    parser.add_argument('--port', type=int, default=5432, help='Database port')
    parser.add_argument('--user', default='postgres', help='Database user')
    parser.add_argument('--dbname', default='postgres', help='Database name')
    parser.add_argument('--password', default='ontop', help='Database password')
    parser.add_argument('--class', dest='class_filter', help='Analyze only this class (short name)')
    parser.add_argument('--min-instances', type=int, default=0, help='Minimum instance count to include class')
    parser.add_argument('--detailed', action='store_true', help='Show detailed value statistics')

    args = parser.parse_args()

    print(f"Connecting to PostgreSQL at {args.host}:{args.port}...")
    conn = get_connection(args.host, args.port, args.user, args.dbname, args.password)

    try:
        cursor = conn.cursor()
        check_triples_table(cursor)

        # Get all classes
        all_classes = get_all_classes(cursor)

        # Filter classes if requested
        if args.class_filter:
            all_classes = [c for c in all_classes if args.class_filter in c]
            if not all_classes:
                print(f"No classes found matching '{args.class_filter}'")
                return

        print("=" * 80)
        print("PREDICATE USAGE ANALYSIS BY CLASS")
        print("=" * 80)
        print()

        # Initialize results structure for JSON output
        results = {
            "analysis_metadata": {
                "timestamp": datetime.now().isoformat(),
                "triples_table": TRIPLES_TABLE,
                "filters": {
                    "class_filter": args.class_filter,
                    "min_instances": args.min_instances
                },
                "detailed": args.detailed
            },
            "classes": []
        }

        # Analyze each class
        for class_uri in all_classes:
            instance_count = count_instances(cursor, class_uri)

            # Skip classes with too few instances
            if instance_count < args.min_instances:
                continue

            class_name = shorten_uri(class_uri)
            print(f"\n{'=' * 80}")
            print(f"Class: {class_name}")
            print(f"Instances: {instance_count:,}")
            print(f"{'=' * 80}")

            # Initialize class data for JSON output
            class_data = {
                "class_uri": class_uri,
                "class_name": class_name,
                "instance_count": instance_count,
                "predicates": []
            }

            # Get class references (incoming and outgoing)
            incoming_refs, outgoing_refs = get_class_references(cursor, class_uri)
            class_data["referenced_by"] = incoming_refs
            class_data["references_to"] = outgoing_refs

            # Print reference summary
            if incoming_refs or outgoing_refs:
                print(f"\nClass Relationships:")
                if incoming_refs:
                    print(f"  Referenced by {len(incoming_refs)} class(es):")
                    for ref in incoming_refs[:5]:  # Show top 5
                        pred_names = ', '.join([p['predicate_short'] for p in ref['predicates'][:3]])
                        if len(ref['predicates']) > 3:
                            pred_names += f", ... ({len(ref['predicates'])} total)"
                        print(f"    - {ref['class_name']} ({ref['reference_count']} refs via {pred_names})")
                    if len(incoming_refs) > 5:
                        print(f"    ... and {len(incoming_refs) - 5} more")

                if outgoing_refs:
                    print(f"  References {len(outgoing_refs)} class(es):")
                    for ref in outgoing_refs[:5]:  # Show top 5
                        pred_names = ', '.join([p['predicate_short'] for p in ref['predicates'][:3]])
                        if len(ref['predicates']) > 3:
                            pred_names += f", ... ({len(ref['predicates'])} total)"
                        print(f"    - {ref['class_name']} ({ref['reference_count']} refs via {pred_names})")
                    if len(outgoing_refs) > 5:
                        print(f"    ... and {len(outgoing_refs) - 5} more")

            predicates = get_predicates_for_class(cursor, class_uri)

            if not predicates:
                print("  No predicates found")
                # Still add class data even if no predicates found
                results["classes"].append(class_data)
                continue

            # Group predicates by namespace
            by_namespace = defaultdict(list)
            for pred, count in predicates:
                short_pred = shorten_uri(pred)
                namespace = short_pred.split(':')[0] if ':' in short_pred else 'other'
                by_namespace[namespace].append((pred, short_pred, count))

            # Display predicates grouped by namespace
            for namespace in sorted(by_namespace.keys()):
                print(f"\n  {namespace.upper()} predicates:")
                for pred_uri, pred_short, usage_count in by_namespace[namespace]:
                    coverage_pct = (usage_count / instance_count) * 100
                    print(f"    {pred_short:50s} ({usage_count:,} uses, {coverage_pct:.1f}% coverage)")

                    # Collect predicate data for JSON output
                    pred_data = {
                        "predicate_uri": pred_uri,
                        "predicate_short": pred_short,
                        "namespace": namespace,
                        "usage_count": usage_count,
                        "coverage_percentage": round(coverage_pct, 1)
                    }

                    if args.detailed:
                        stats = analyze_predicate_values(cursor, class_uri, pred_uri)
                        if stats and stats[0] > 0:
                            total, distinct, min_len, max_len = stats
                            print(f"      → {distinct:,} distinct values, length: {min_len}-{max_len}")
                            pred_data["value_statistics"] = {
                                "total_values": total,
                                "distinct_values": distinct,
                                "min_length": min_len,
                                "max_length": max_len
                            }

                    class_data["predicates"].append(pred_data)

            # Add class data to results after processing all predicates
            results["classes"].append(class_data)

        print("\n" + "=" * 80)
        print("ANALYSIS COMPLETE")
        print("=" * 80)

        # Sort results before writing to JSON
        # Sort classes by instance_count (descending - most instances first)
        results["classes"].sort(key=lambda x: x["instance_count"], reverse=True)

        # Sort predicates by coverage_percentage (descending - highest coverage first)
        for class_data in results["classes"]:
            class_data["predicates"].sort(key=lambda x: x["coverage_percentage"], reverse=True)

        # Write results to JSON file
        output_filename = "class_predicates_analysis.json"
        with open(output_filename, 'w') as f:
            json.dump(results, f, indent=2)

        print(f"\nResults saved to: {output_filename}")
        print(f"Total classes analyzed: {len(results['classes'])}")

        cursor.close()

    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
