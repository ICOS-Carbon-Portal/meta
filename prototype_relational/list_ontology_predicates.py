#!/usr/bin/python

from rdflib import Graph, RDF, OWL
from collections import defaultdict
import psycopg2


def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )


def get_predicate_counts():
    """Get counts of each predicate from the rdf_triples table."""
    conn = get_connection()
    cursor = conn.cursor()

    query = """
        SELECT pred, COUNT(*) as count
        FROM rdf_triples
        GROUP BY pred;
    """

    cursor.execute(query)
    results = cursor.fetchall()

    cursor.close()
    conn.close()

    # Convert to dictionary for easy lookup
    return {pred: count for pred, count in results}


def parse_ontology(owl_file):
    """Parse the OWL ontology file and extract all predicates."""
    g = Graph()
    g.parse(owl_file, format="xml")
    return g


def extract_predicates(graph):
    """Extract all ObjectProperty and DatatypeProperty from the ontology."""
    # Query for all ObjectProperties
    object_properties = set()
    for prop in graph.subjects(RDF.type, OWL.ObjectProperty):
        object_properties.add(str(prop))

    # Query for all DatatypeProperties
    datatype_properties = set()
    for prop in graph.subjects(RDF.type, OWL.DatatypeProperty):
        datatype_properties.add(str(prop))

    return object_properties, datatype_properties


def group_by_namespace(predicates, counts):
    """Group predicates by their namespace with counts."""
    namespaces = defaultdict(list)

    for pred in sorted(predicates):
        # Extract namespace (everything before the last # or /)
        if '#' in pred:
            namespace = pred.rsplit('#', 1)[0] + '#'
            local_name = pred.rsplit('#', 1)[1]
        elif '/' in pred:
            namespace = pred.rsplit('/', 1)[0] + '/'
            local_name = pred.rsplit('/', 1)[1]
        else:
            namespace = "no-namespace"
            local_name = pred

        count = counts.get(pred, 0)
        namespaces[namespace].append((pred, local_name, count))

    return namespaces


def main():
    owl_file = "ontop/cpmeta.owl"

    print("Parsing ontology file: cpmeta.owl")
    print("Querying rdf_triples table for predicate counts...")
    print()

    # Get predicate counts from database
    db_counts = get_predicate_counts()

    # Parse the ontology
    graph = parse_ontology(owl_file)

    # Extract predicates
    object_props, datatype_props = extract_predicates(graph)
    all_predicates = object_props | datatype_props

    # Calculate statistics
    used_predicates = {p for p in all_predicates if db_counts.get(p, 0) > 0}
    unused_predicates = all_predicates - used_predicates
    undefined_predicates = {p for p in db_counts.keys() if p not in all_predicates}

    # Count total entries by predicate type
    object_prop_entries = sum(db_counts.get(p, 0) for p in object_props)
    datatype_prop_entries = sum(db_counts.get(p, 0) for p in datatype_props)
    total_db_entries = sum(db_counts.values())

    # Display summary
    print("=" * 120)
    print("SUMMARY")
    print("=" * 120)
    print(f"Total unique predicates in ontology:     {len(all_predicates)}")
    print(f"  - Object Properties:                   {len(object_props)}")
    print(f"  - Datatype Properties:                 {len(datatype_props)}")
    print()
    print(f"Predicates used in rdf_triples:          {len(used_predicates)} ({100*len(used_predicates)/len(all_predicates):.1f}% of ontology)")
    print(f"Predicates in ontology but not in DB:    {len(unused_predicates)}")
    print(f"Predicates in DB but not in ontology:    {len(undefined_predicates)}")
    print()
    print(f"Total entries in rdf_triples:            {total_db_entries:,}")
    print(f"  - Object Property entries:             {object_prop_entries:,}")
    print(f"  - Datatype Property entries:           {datatype_prop_entries:,}")
    if undefined_predicates:
        undefined_entries = sum(db_counts.get(p, 0) for p in undefined_predicates)
        print(f"  - Undefined predicate entries:         {undefined_entries:,}")
    print()
    print("=" * 120)

    # Display Object Properties
    print()
    print("OBJECT PROPERTIES (properties linking to other resources)")
    print("=" * 120)

    obj_namespaces = group_by_namespace(object_props, db_counts)
    for ns in sorted(obj_namespaces.keys()):
        print()
        print(f"Namespace: {ns}")
        print("-" * 120)
        for full_uri, local_name, count in obj_namespaces[ns]:
            count_str = f"{count:,}" if count > 0 else "0"
            status = "" if count > 0 else " [NOT IN DB]"
            print(f"  {local_name:<45} | Count: {count_str:>12}{status}")

    # Display Datatype Properties
    print()
    print()
    print("DATATYPE PROPERTIES (properties with literal values)")
    print("=" * 120)

    data_namespaces = group_by_namespace(datatype_props, db_counts)
    for ns in sorted(data_namespaces.keys()):
        print()
        print(f"Namespace: {ns}")
        print("-" * 120)
        for full_uri, local_name, count in data_namespaces[ns]:
            count_str = f"{count:,}" if count > 0 else "0"
            status = "" if count > 0 else " [NOT IN DB]"
            print(f"  {local_name:<45} | Count: {count_str:>12}{status}")

    # Display predicates in DB but not in ontology
    if undefined_predicates:
        print()
        print()
        print("PREDICATES IN DATABASE BUT NOT IN ONTOLOGY")
        print("=" * 120)
        print("These predicates appear in rdf_triples but are not defined in cpmeta.owl:")
        print("-" * 120)
        for pred in sorted(undefined_predicates):
            count = db_counts[pred]
            print(f"  {pred:<80} | Count: {count:>12,}")

    print()
    print("=" * 120)


if __name__ == "__main__":
    try:
        main()
    except ImportError as e:
        if 'rdflib' in str(e):
            print("Error: rdflib is not installed.")
            print("Please install it with: pip install rdflib")
        elif 'psycopg2' in str(e):
            print("Error: psycopg2 is not installed.")
            print("Please install it with: pip install psycopg2")
        else:
            raise
    except FileNotFoundError:
        print("Error: Could not find ontop/cpmeta.owl")
        print("Please run this script from the prototype_relational directory")
    except psycopg2.OperationalError as e:
        print(f"Error: Could not connect to the database.")
        print(f"Details: {e}")
        print("Please ensure PostgreSQL is running and credentials are correct.")
