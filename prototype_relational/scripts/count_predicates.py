#!/usr/bin/python

import psycopg2


def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )


def count_predicates():
    """Count entries for each unique predicate in the rdf_triples table."""
    conn = get_connection()
    cursor = conn.cursor()

    # Query to count entries per unique predicate
    query = """
        SELECT pred, COUNT(*) as count
        FROM rdf_triples
        GROUP BY pred
        ORDER BY count DESC;
    """

    cursor.execute(query)
    predicate_counts = cursor.fetchall()

    cursor.close()
    conn.close()

    return predicate_counts


def main():
    print("Counting entries per unique predicate in rdf_triples table...")
    print()

    predicate_counts = count_predicates()

    print(f"Total unique predicates: {len(predicate_counts)}\n")
    print("-" * 100)
    print(f"{'Predicate':<80} {'Count':>15}")
    print("-" * 100)

    for pred, count in predicate_counts:
        print(f"{pred:<80} {count:>15,}")

    print("-" * 100)
    total_entries = sum(count for _, count in predicate_counts)
    print(f"{'TOTAL ENTRIES':<80} {total_entries:>15,}")


if __name__ == "__main__":
    main()
