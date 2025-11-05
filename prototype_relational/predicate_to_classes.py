#!/usr/bin/python

import json
import psycopg2
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


def get_predicate_object_classes():
    """
    Query the database to find which classes the objects of each predicate belong to.
    Returns: dict mapping predicate_uri -> list of (class_uri, class_name, count)
    """
    print("Querying database for predicate object classes...")
    conn = get_connection()
    cursor = conn.cursor()

    query = """
        SELECT
            t.pred,
            obj_class.obj as object_class_uri,
            COUNT(*) as usage_count
        FROM rdf_triples t
        JOIN rdf_triples obj_class ON obj_class.subj = t.obj
            AND obj_class.pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
        GROUP BY t.pred, obj_class.obj
        ORDER BY t.pred, usage_count DESC
    """

    cursor.execute(query)
    results = cursor.fetchall()

    cursor.close()
    conn.close()

    # Organize by predicate
    predicate_objects = defaultdict(list)
    for pred_uri, obj_class_uri, count in results:
        predicate_objects[pred_uri].append({
            'class_uri': obj_class_uri,
            'class_name': shorten_uri(obj_class_uri),
            'count': count
        })

    print(f"Found object class information for {len(predicate_objects)} predicates")
    return dict(predicate_objects)


def load_class_predicates_analysis(filename="scripts/class_predicates_analysis.json"):
    """Load the class predicates analysis JSON file."""
    with open(filename, 'r', encoding='utf-8') as f:
        return json.load(f)


def invert_to_predicate_centric(data, object_classes_map):
    """
    Invert the class-centric structure to predicate-centric.

    Input: classes -> predicates
    Output: predicates -> classes (with object class information)
    """
    predicates = defaultdict(lambda: {
        'predicate_uri': None,
        'predicate_short': None,
        'namespace': None,
        'total_usage': 0,
        'used_by_classes': [],
        'points_to_classes': []
    })

    # Iterate through all classes
    for class_info in data['classes']:
        class_uri = class_info['class_uri']
        class_name = class_info['class_name']

        # Iterate through predicates used by this class
        for pred_info in class_info['predicates']:
            pred_uri = pred_info['predicate_uri']

            # Initialize predicate info if first time seeing it
            if predicates[pred_uri]['predicate_uri'] is None:
                predicates[pred_uri]['predicate_uri'] = pred_uri
                predicates[pred_uri]['predicate_short'] = pred_info['predicate_short']
                predicates[pred_uri]['namespace'] = pred_info['namespace']
                # Add object class information from database query
                if pred_uri in object_classes_map:
                    predicates[pred_uri]['points_to_classes'] = object_classes_map[pred_uri]

            # Add this class as a user of this predicate
            predicates[pred_uri]['used_by_classes'].append({
                'class_uri': class_uri,
                'class_name': class_name,
                'usage_count': pred_info['usage_count'],
                'coverage_percentage': pred_info['coverage_percentage']
            })

            # Add to total usage
            predicates[pred_uri]['total_usage'] += pred_info['usage_count']

    # Convert defaultdict to regular dict and sort classes by usage
    result = {}
    for pred_uri, pred_data in predicates.items():
        pred_data['used_by_classes'].sort(key=lambda x: x['usage_count'], reverse=True)
        result[pred_uri] = pred_data

    return result


def group_by_namespace(predicates):
    """Group predicates by namespace."""
    namespaces = defaultdict(list)

    for pred_uri, pred_data in predicates.items():
        namespace = pred_data['namespace']
        namespaces[namespace].append({
            'predicate_uri': pred_uri,
            'predicate_short': pred_data['predicate_short'],
            'total_usage': pred_data['total_usage'],
            'num_classes': len(pred_data['used_by_classes']),
            'used_by_classes': pred_data['used_by_classes'],
            'points_to_classes': pred_data['points_to_classes']
        })

    # Sort predicates within each namespace by total usage
    for ns in namespaces:
        namespaces[ns].sort(key=lambda x: x['total_usage'], reverse=True)

    return dict(namespaces)


def write_json_output(predicates, namespaces, filename="predicate_to_classes.json"):
    """Write the results to a JSON file."""
    output = {
        'summary': {
            'total_predicates': len(predicates),
            'total_usage': sum(p['total_usage'] for p in predicates.values()),
            'namespaces': list(namespaces.keys())
        },
        'predicates': predicates,
        'by_namespace': namespaces
    }

    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"\nJSON output written to: {filename}")


def main():
    print("Loading class predicates analysis from scripts/class_predicates_analysis.json...")
    print()

    # Load the class-centric data
    data = load_class_predicates_analysis()

    # Query database for object class information
    object_classes_map = get_predicate_object_classes()
    print()

    # Invert to predicate-centric
    predicates = invert_to_predicate_centric(data, object_classes_map)

    # Group by namespace
    namespaces = group_by_namespace(predicates)

    # Display summary
    total_predicates = len(predicates)
    total_usage = sum(p['total_usage'] for p in predicates.values())

    print("=" * 140)
    print("SUMMARY")
    print("=" * 140)
    print(f"Total unique predicates:                 {total_predicates}")
    print(f"Total predicate usage across all classes: {total_usage:,}")
    print(f"Number of namespaces:                    {len(namespaces)}")
    print()
    print("=" * 140)

    # Display predicates grouped by namespace
    print()
    print("PREDICATES AND THEIR ASSOCIATED CLASSES (BY NAMESPACE)")
    print("=" * 140)

    for ns in sorted(namespaces.keys()):
        print()
        print(f"Namespace: {ns}")
        print("-" * 140)

        for pred in namespaces[ns]:
            print(f"\n  {pred['predicate_short']}")
            print(f"  Full URI: {pred['predicate_uri']}")
            print(f"  Total Usage: {pred['total_usage']:,} | Used by {pred['num_classes']} class(es)")

            print(f"  Used by (subjects):")
            for class_info in pred['used_by_classes']:
                print(f"    - {class_info['class_name']:<50} | Usage: {class_info['usage_count']:>10,} | Coverage: {class_info['coverage_percentage']:>6.1f}%")

            if pred['points_to_classes']:
                print(f"  Points to (objects):")
                for obj_class in pred['points_to_classes'][:10]:  # Limit to top 10
                    print(f"    - {obj_class['class_name']:<50} | Count: {obj_class['count']:>10,}")
                if len(pred['points_to_classes']) > 10:
                    print(f"    ... and {len(pred['points_to_classes']) - 10} more class(es)")

    print()
    print("=" * 140)

    # Top predicates by number of classes using them
    print()
    print("TOP 20 PREDICATES BY NUMBER OF CLASSES USING THEM")
    print("=" * 140)
    print(f"{'Predicate (Full URI)':<80} | {'Classes':>8} | {'Total Usage':>12}")
    print("-" * 140)

    sorted_by_class_count = sorted(
        predicates.items(),
        key=lambda x: len(x[1]['used_by_classes']),
        reverse=True
    )[:20]

    for pred_uri, pred_data in sorted_by_class_count:
        num_classes = len(pred_data['used_by_classes'])
        total_usage = pred_data['total_usage']
        print(f"{pred_uri:<80} | {num_classes:>8} | {total_usage:>12,}")

    print()
    print("=" * 140)

    # Top predicates by total usage
    print()
    print("TOP 20 PREDICATES BY TOTAL USAGE")
    print("=" * 140)
    print(f"{'Predicate (Full URI)':<80} | {'Total Usage':>12} | {'Classes':>8}")
    print("-" * 140)

    sorted_by_usage = sorted(
        predicates.items(),
        key=lambda x: x[1]['total_usage'],
        reverse=True
    )[:20]

    for pred_uri, pred_data in sorted_by_usage:
        num_classes = len(pred_data['used_by_classes'])
        total_usage = pred_data['total_usage']
        print(f"{pred_uri:<80} | {total_usage:>12,} | {num_classes:>8}")

    print()
    print("=" * 140)

    # Write JSON output
    write_json_output(predicates, namespaces)


if __name__ == "__main__":
    try:
        main()
    except FileNotFoundError as e:
        print(f"Error: Could not find the input file.")
        print(f"Details: {e}")
        print("Please ensure scripts/class_predicates_analysis.json exists.")
        print("Generate it by running: python3 scripts/analyze_class_predicates.py")
    except json.JSONDecodeError as e:
        print(f"Error: Invalid JSON in input file.")
        print(f"Details: {e}")
    except psycopg2.OperationalError as e:
        print(f"Error: Could not connect to the database.")
        print(f"Details: {e}")
        print("Please ensure PostgreSQL is running and credentials are correct.")
    except Exception as e:
        print(f"Error: {e}")
        raise
