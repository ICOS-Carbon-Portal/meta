#!/usr/bin/env python3
"""
Populate RDF triples table from CSV.
Replacement for scripts/populate_rdf_triples.sh
"""
import sys
import os
sys.path.insert(0, '..')
from db_connection import get_connection

def populate_rdf_triples(csv_path=None):
    """Load RDF triples from CSV into DuckDB."""
    if csv_path is None:
        # Default CSV path (adjust as needed)
        csv_path = os.path.join(os.path.dirname(__file__), '..', 'dumps', 'dump_full.csv')

    conn = get_connection()

    print("Creating rdf_triples table...")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS rdf_triples (
            subj TEXT,
            pred TEXT,
            obj TEXT
        )
    """)

    print(f"Loading CSV from {csv_path}...")
    # DuckDB has excellent CSV support
    conn.execute(f"""
        COPY rdf_triples FROM '{csv_path}' (FORMAT CSV)
    """)

    print("Purging fieldsites entries...")
    conn.execute("""
        DELETE FROM rdf_triples
        WHERE subj LIKE 'https://meta.fieldsites.se%'
           OR pred LIKE 'https://meta.fieldsites.se%'
           OR obj LIKE 'https://meta.fieldsites.se%'
    """)

    print("Creating indexes...")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_rdf_triples_subj ON rdf_triples(subj)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_rdf_triples_pred ON rdf_triples(pred)")

    conn.commit()

    # Get count
    count = conn.execute("SELECT COUNT(*) FROM rdf_triples").fetchone()[0]
    print(f"✓ RDF triples loaded successfully: {count} rows")

if __name__ == "__main__":
    csv_path = sys.argv[1] if len(sys.argv) > 1 else None
    populate_rdf_triples(csv_path)
