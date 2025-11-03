#!/usr/bin/python

import psycopg2
import sys

def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )

def populate_hasObjectSpec():
    """
    Populate the hasObjectSpec column in data_objects table.

    This script updates existing data_objects entries by looking up the
    hasObjectSpec property from rdf_triples where the subject matches.
    """
    print("Connecting to database...")
    conn = get_connection()
    cursor = conn.cursor()

    try:
        # Check if the column exists
        cursor.execute("""
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = 'data_objects'
              AND column_name = 'hasobjectspec'
        """)

        if cursor.fetchone() is None:
            print("ERROR: Column 'hasObjectSpec' does not exist in data_objects table")
            print("Please ensure the table has been updated with the new column")
            sys.exit(1)

        print("Found hasObjectSpec column")

        # Get count of data_objects before update
        cursor.execute("SELECT COUNT(*) FROM data_objects")
        total_rows = cursor.fetchone()[0]
        print(f"Total data objects in table: {total_rows}")

        # Get count of how many already have hasObjectSpec populated
        cursor.execute("SELECT COUNT(*) FROM data_objects WHERE hasObjectSpec IS NOT NULL")
        already_populated = cursor.fetchone()[0]
        print(f"Already populated: {already_populated}")

        # Populate hasObjectSpec column
        print("\nPopulating hasObjectSpec column...")

        object_spec_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec"

        update_query = """
            UPDATE data_objects
            SET hasObjectSpec = rdf.obj
            FROM rdf_triples rdf
            WHERE rdf.subj = data_objects.subject
              AND rdf.pred = %s
              AND data_objects.hasObjectSpec IS DISTINCT FROM rdf.obj
        """

        cursor.execute(update_query, (object_spec_pred,))
        rows_updated = cursor.rowcount

        conn.commit()
        print(f"Updated {rows_updated} rows")

        # Get final statistics
        cursor.execute("SELECT COUNT(*) FROM data_objects WHERE hasObjectSpec IS NOT NULL")
        now_populated = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM data_objects WHERE hasObjectSpec IS NULL")
        still_null = cursor.fetchone()[0]

        print(f"\nFinal statistics:")
        print(f"  Rows with hasObjectSpec: {now_populated}")
        print(f"  Rows without hasObjectSpec: {still_null}")

        # Create index
        print("\nCreating index on hasObjectSpec...")

        index_query = """
            CREATE INDEX IF NOT EXISTS idx_data_objects_spec
            ON data_objects(hasObjectSpec)
        """

        cursor.execute(index_query)
        conn.commit()

        print("Index created successfully")

        # Verify index was created
        cursor.execute("""
            SELECT indexname
            FROM pg_indexes
            WHERE tablename = 'data_objects'
              AND indexname = 'idx_data_objects_spec'
        """)

        if cursor.fetchone():
            print("✓ Index verified: idx_data_objects_spec")

        print("\n✓ Population complete!")

    except psycopg2.Error as e:
        print(f"\nDatabase error occurred: {e}")
        conn.rollback()
        sys.exit(1)
    except Exception as e:
        print(f"\nUnexpected error occurred: {e}")
        conn.rollback()
        sys.exit(1)
    finally:
        cursor.close()
        conn.close()


def main():
    print("=" * 60)
    print("Populate hasObjectSpec Column")
    print("=" * 60)
    print()

    populate_hasObjectSpec()

if __name__ == "__main__":
    main()
