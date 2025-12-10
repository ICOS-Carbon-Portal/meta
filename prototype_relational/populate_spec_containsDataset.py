#!/usr/bin/python

import duckdb
from db_connection import get_connection
import sys

def populate_spec_containsDataset():
    """
    Populate the spec_containsDataset column in data_objects table.

    This script updates existing data_objects entries by looking up the
    containsDataset property from their associated ObjectSpec in rdf_triples.
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
              AND column_name = 'spec_containsdataset'
        """)

        if cursor.fetchone() is None:
            print("ERROR: Column 'spec_containsDataset' does not exist in data_objects table")
            print("Please ensure the table has been updated with the new column")
            sys.exit(1)

        print("Found spec_containsDataset column")

        # Get count of data_objects before update
        cursor.execute("SELECT COUNT(*) FROM data_objects")
        total_rows = cursor.fetchone()[0]
        print(f"Total data objects in table: {total_rows}")

        # Get count of how many already have spec_containsDataset populated
        cursor.execute("SELECT COUNT(*) FROM data_objects WHERE spec_containsDataset IS NOT NULL")
        already_populated = cursor.fetchone()[0]
        print(f"Already populated: {already_populated}")

        # Populate spec_containsDataset column
        print("\nPopulating spec_containsDataset column...")

        containsDataset_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/containsDataset"

        update_query = """
            UPDATE data_objects
            SET spec_containsDataset = rdf.obj
            FROM rdf_triples rdf
            WHERE rdf.subj = data_objects.hasObjectSpec
              AND rdf.pred = ?
              AND data_objects.spec_containsDataset IS DISTINCT FROM rdf.obj
        """

        cursor.execute(update_query, (containsDataset_pred,))
        rows_updated = cursor.rowcount

        conn.commit()
        print(f"Updated {rows_updated} rows")

        # Get final statistics
        cursor.execute("SELECT COUNT(*) FROM data_objects WHERE spec_containsDataset IS NOT NULL")
        now_populated = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM data_objects WHERE spec_containsDataset IS NULL")
        still_null = cursor.fetchone()[0]

        print(f"\nFinal statistics:")
        print(f"  Rows with spec_containsDataset: {now_populated}")
        print(f"  Rows without spec_containsDataset: {still_null}")

        # Create index
        print("\nCreating index on spec_containsDataset...")

        index_query = """
            CREATE INDEX IF NOT EXISTS idx_data_objects_spec_containsDataset
            ON data_objects(spec_containsDataset)
        """

        cursor.execute(index_query)
        conn.commit()

        print("Index created successfully")

        # Verify index was created
        cursor.execute("""
            SELECT indexname
            FROM pg_indexes
            WHERE tablename = 'data_objects'
              AND indexname = 'idx_data_objects_spec_containsdataset'
        """)

        if cursor.fetchone():
            print("✓ Index verified: idx_data_objects_spec_containsDataset")

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


def populate_spec_dataset_field(db_column, predicate):
    print("Connecting to database...")
    conn = get_connection()
    cursor = conn.cursor()

    # Check if the column exists
    cursor.execute(f"""
        SELECT column_name
        FROM information_schema.columns
        WHERE table_name = 'data_objects'
          AND column_name = '{db_column}'
    """)

    if cursor.fetchone() is None:
        print(f"ERROR: Column '{db_column}' does not exist in data_objects table")
        print("Please ensure the table has been updated with the new column")
        sys.exit(1)

    print(f"Found {db_column} column")

    # Get count of data_objects before update
    cursor.execute("SELECT COUNT(*) FROM data_objects")
    total_rows = cursor.fetchone()[0]
    print(f"Total data objects in table: {total_rows}")

    cursor.execute(f"SELECT COUNT(*) FROM data_objects WHERE {db_column} IS NOT NULL")
    already_populated = cursor.fetchone()[0]
    print(f"Already populated: {already_populated}")

    print("\nPopulating column...")


    update_query = f"""
        UPDATE data_objects
        SET {db_column} = rdf.obj
        FROM rdf_triples rdf
        WHERE rdf.subj = data_objects.spec_containsDataset
          AND rdf.pred = ?
          AND data_objects.{db_column} IS DISTINCT FROM rdf.obj
    """

    cursor.execute(update_query, (predicate,))
    rows_updated = cursor.rowcount

    conn.commit()
    print(f"Updated {rows_updated} rows")

    # Get final statistics
    cursor.execute(f"SELECT COUNT(*) FROM data_objects WHERE {db_column} IS NOT NULL")
    now_populated = cursor.fetchone()[0]

    cursor.execute(f"SELECT COUNT(*) FROM data_objects WHERE {db_column} IS NULL")
    still_null = cursor.fetchone()[0]

    print(f"\nFinal statistics:")
    print(f"  Rows with {db_column}: {now_populated}")
    print(f"  Rows without {db_column}: {still_null}")

    # Create index
    print(f"\nCreating index on {db_column}...")

    index_name = f"idx_data_objects_{db_column}"
    index_query = f"""
        CREATE INDEX IF NOT EXISTS {index_name}
        ON data_objects({db_column})
    """

    cursor.execute(index_query)
    conn.commit()

    print("Index created successfully")

    # Verify index was created
    cursor.execute("""
        SELECT indexname
        FROM pg_indexes
        WHERE tablename = 'data_objects'
          AND indexname = '{index_name}'
    """)

    if cursor.fetchone():
        print(f"✓ Index verified: {index_name}")

    print("\n✓ Population complete!")

    conn.commit()
    cursor.close()
    conn.close()


def main():
    print("=" * 60)
    print("Populate spec_containsDataset Column")
    print("=" * 60)
    print()

    # populate_spec_containsDataset()
    populate_spec_dataset_field('spec_dataset_hascolumn', 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn')

if __name__ == "__main__":
    main()
