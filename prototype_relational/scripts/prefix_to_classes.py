#!/usr/bin/env python

import psycopg2
import json
from datetime import datetime
from collections import defaultdict


def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )


def shorten_uri(uri):
    """Shorten a URI to just the local name."""
    if '#' in uri:
        return uri.rsplit('#', 1)[1]
    elif '/' in uri:
        return uri.rsplit('/', 1)[1]
    return uri


def load_subject_prefixes(filename='subject_prefixes.json'):
    """Load the subject prefixes from JSON file."""
    with open(filename, 'r') as f:
        data = json.load(f)
    return data['prefix_counts']


def get_subject_classes(conn, prefix):
    """
    Get RDF classes for subjects with the given prefix.
    Returns list of (class_uri, count) tuples.
    """
    cursor = conn.cursor()

    query = """
        SELECT class.obj as class_uri, COUNT(DISTINCT t.subj) as count
        FROM rdf_triples t
        JOIN rdf_triples class ON class.subj = t.subj
            AND class.pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
        WHERE t.subj LIKE %s
        GROUP BY class.obj
        ORDER BY count DESC
    """

    cursor.execute(query, (prefix + '%',))
    results = cursor.fetchall()
    cursor.close()

    return results


def get_object_classes(conn, prefix):
    """
    Get RDF classes for when subjects with this prefix are used as objects.
    Returns list of (class_uri, count) tuples.
    """
    cursor = conn.cursor()

    query = """
        SELECT class.obj as class_uri, COUNT(DISTINCT t.obj) as count
        FROM rdf_triples t
        JOIN rdf_triples class ON class.subj = t.obj
            AND class.pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
        WHERE t.obj LIKE %s
        GROUP BY class.obj
        ORDER BY count DESC
    """

    cursor.execute(query, (prefix + '%',))
    results = cursor.fetchall()
    cursor.close()

    return results


def get_predicates_as_subject(conn, prefix):
    """
    Get predicates used by subjects with this prefix.
    Returns list of (predicate_uri, count) tuples.
    """
    cursor = conn.cursor()

    query = """
        SELECT pred, COUNT(*) as count
        FROM rdf_triples
        WHERE subj LIKE %s
        GROUP BY pred
        ORDER BY count DESC
    """

    cursor.execute(query, (prefix + '%',))
    results = cursor.fetchall()
    cursor.close()

    return results


def get_predicates_as_object(conn, prefix):
    """
    Get predicates where subjects with this prefix appear as objects.
    Returns list of (predicate_uri, count) tuples.
    """
    cursor = conn.cursor()

    query = """
        SELECT pred, COUNT(*) as count
        FROM rdf_triples
        WHERE obj LIKE %s
        GROUP BY pred
        ORDER BY count DESC
    """

    cursor.execute(query, (prefix + '%',))
    results = cursor.fetchall()
    cursor.close()

    return results


def calculate_percentages(items, total):
    """Add percentage field to each item based on total."""
    result = []
    for item in items:
        item_with_pct = dict(item)
        if total > 0:
            item_with_pct['percentage'] = round((item['count'] / total) * 100, 2)
        else:
            item_with_pct['percentage'] = 0.0
        result.append(item_with_pct)
    return result


def analyze_prefix(conn, prefix, total_count):
    """
    Perform comprehensive analysis for a single prefix.
    Returns dict with subject_classes, object_classes, preds_subject, preds_object.
    """
    print(f"  Analyzing prefix: {prefix[:60]}... (count: {total_count:,})")

    # Get subject classes
    subject_classes_raw = get_subject_classes(conn, prefix)
    subject_classes = [
        {
            'class_uri': class_uri,
            'class_name': shorten_uri(class_uri),
            'count': count
        }
        for class_uri, count in subject_classes_raw
    ]
    subject_classes = calculate_percentages(subject_classes, total_count)

    # Get object classes
    object_classes_raw = get_object_classes(conn, prefix)
    object_classes = [
        {
            'class_uri': class_uri,
            'class_name': shorten_uri(class_uri),
            'count': count
        }
        for class_uri, count in object_classes_raw
    ]
    # For object classes, calculate percentage based on actual objects with this prefix
    object_total = sum(item['count'] for item in object_classes)
    object_classes = calculate_percentages(object_classes, object_total)

    # Get predicates where subject has prefix
    predicates_as_subject_raw = get_predicates_as_subject(conn, prefix)
    preds_subject = [
        {
            'predicate_uri': pred_uri,
            'predicate_name': shorten_uri(pred_uri),
            'count': count
        }
        for pred_uri, count in predicates_as_subject_raw
    ]

    # Get predicates where object has prefix
    predicates_as_object_raw = get_predicates_as_object(conn, prefix)
    preds_object = [
        {
            'predicate_uri': pred_uri,
            'predicate_name': shorten_uri(pred_uri),
            'count': count
        }
        for pred_uri, count in predicates_as_object_raw
    ]

    return {
        'total_count': total_count,
        'subject_classes': subject_classes,
        'object_classes': object_classes,
        'preds_subject': preds_subject,
        'preds_object': preds_object
    }


def main():
    print("=" * 80)
    print("PREFIX TO CLASSES ANALYSIS")
    print("=" * 80)
    print("Loading prefixes from subject_prefixes.json...")

    # Load prefixes
    prefix_counts = load_subject_prefixes()
    total_prefixes = len(prefix_counts)

    print(f"Found {total_prefixes} prefixes to analyze")
    print()
    print("Connecting to database...")

    # Connect to database
    conn = get_connection()

    # Analyze each prefix
    results = {}
    print()
    print("Analyzing each prefix...")
    print("-" * 80)

    for i, (prefix, count) in enumerate(prefix_counts.items(), 1):
        print(f"[{i}/{total_prefixes}]", end=" ")
        results[prefix] = analyze_prefix(conn, prefix, count)

    conn.close()

    # Prepare output
    output = {
        'timestamp': datetime.now().isoformat(),
        'total_prefixes': total_prefixes,
        'prefixes': results
    }

    # Save to file
    output_file = 'prefix_to_classes.json'
    print()
    print("-" * 80)
    print(f"Saving results to {output_file}...")

    with open(output_file, 'w') as f:
        json.dump(output, f, indent=2)

    print(f"✓ Results saved to {output_file}")
    print()

    # Print summary
    print("=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Total prefixes analyzed: {total_prefixes}")

    # Show some interesting statistics
    prefixes_with_subject_classes = sum(1 for p in results.values() if p['subject_classes'])
    prefixes_with_object_classes = sum(1 for p in results.values() if p['object_classes'])

    print(f"Prefixes with subject classes: {prefixes_with_subject_classes}")
    print(f"Prefixes with object classes: {prefixes_with_object_classes}")

    print()
    print("Sample results for first prefix:")
    print("-" * 80)

    first_prefix = list(prefix_counts.keys())[0]
    first_result = results[first_prefix]

    print(f"Prefix: {first_prefix}")
    print(f"Total count: {first_result['total_count']:,}")
    print()

    if first_result['subject_classes']:
        print("Subject classes (top 3):")
        for cls in first_result['subject_classes'][:3]:
            print(f"  - {cls['class_name']}: {cls['count']:,} ({cls['percentage']:.1f}%)")
        print()

    if first_result['object_classes']:
        print("Object classes (top 3):")
        for cls in first_result['object_classes'][:3]:
            print(f"  - {cls['class_name']}: {cls['count']:,} ({cls['percentage']:.1f}%)")
        print()

    if first_result['preds_subject']:
        print(f"Predicates as subject: {len(first_result['preds_subject'])} unique predicates")
        print("Top 3:")
        for pred in first_result['preds_subject'][:3]:
            print(f"  - {pred['predicate_name']}: {pred['count']:,}")
        print()

    if first_result['preds_object']:
        print(f"Predicates as object: {len(first_result['preds_object'])} unique predicates")
        print("Top 3:")
        for pred in first_result['preds_object'][:3]:
            print(f"  - {pred['predicate_name']}: {pred['count']:,}")

    print()
    print("=" * 80)


if __name__ == "__main__":
    try:
        main()
    except FileNotFoundError as e:
        print(f"Error: Could not find subject_prefixes.json")
        print(f"Details: {e}")
        print("Please run scripts/count_prefixes.py first to generate the file.")
    except psycopg2.OperationalError as e:
        print(f"Error: Could not connect to the database.")
        print(f"Details: {e}")
        print("Please ensure PostgreSQL is running and credentials are correct.")
    except Exception as e:
        print(f"Error: {e}")
        raise
