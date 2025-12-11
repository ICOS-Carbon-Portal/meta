#!/usr/bin/env python3
"""
Fix URL-Encoded Characters in rdf_triples Table
===============================================
Problem: Some rdf_triples rows contain URL-encoded characters like %20, %2F, etc.
Solution: Decode all URL-encoded characters to their actual values

Affected columns: subj, pred, obj
"""

import sys
import urllib.parse
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from db_connection import get_connection


def url_decode(text):
    """
    Decode URL-encoded string, handling all %XX patterns.
    Returns original string if it's None or decoding fails.
    """
    if text is None:
        return None
    try:
        return urllib.parse.unquote(text)
    except Exception:
        return text


def has_url_encoding_where_clause(col):
    """
    Returns SQL WHERE clause to detect URL encoding in a column.
    Checks for percent sign followed by two hex digits.
    Uses regexp_matches which works in DuckDB.
    """
    return f"regexp_matches({col}, '.*%[0-9A-Fa-f]{{2}}.*')"


def main():
    print("Connecting to database...")
    conn = get_connection()

    print("=" * 60)
    print("URL Encoding Fix for rdf_triples")
    print("=" * 60)

    # Step 1: Report current state
    print("\n[1/10] Analyzing current state...")

    query = f"""
        SELECT
            COUNT(*) as total_rows,
            SUM(CASE WHEN {has_url_encoding_where_clause('subj')} THEN 1 ELSE 0 END) as subj_encoded,
            SUM(CASE WHEN {has_url_encoding_where_clause('pred')} THEN 1 ELSE 0 END) as pred_encoded,
            SUM(CASE WHEN {has_url_encoding_where_clause('obj')} THEN 1 ELSE 0 END) as obj_encoded
        FROM rdf_triples
    """
    result = conn.execute(query).fetchone()

    total_rows, subj_encoded, pred_encoded, obj_encoded = result

    query2 = f"""
        SELECT COUNT(*)
        FROM rdf_triples
        WHERE {has_url_encoding_where_clause('subj')}
           OR {has_url_encoding_where_clause('pred')}
           OR {has_url_encoding_where_clause('obj')}
    """
    result2 = conn.execute(query2).fetchone()

    total_affected = result2[0]

    print(f"  Total rows in rdf_triples: {total_rows:,}")
    print(f"  Rows with URL encoding: {total_affected:,}")
    print(f"    - subj column: {subj_encoded:,} rows")
    print(f"    - pred column: {pred_encoded:,} rows")
    print(f"    - obj column: {obj_encoded:,} rows")

    if total_affected == 0:
        print("\n✓ No URL-encoded data found. Nothing to do.")
        return 0

    # Step 2: Check existing indexes
    print("\n[2/10] Checking existing indexes...")

    try:
        indexes = conn.execute("""
            SELECT index_name, sql
            FROM duckdb_indexes()
            WHERE table_name = 'rdf_triples'
        """).fetchall()
    except Exception:
        # Fallback if duckdb_indexes() doesn't work
        indexes = []
        print("  Warning: Could not query indexes, will recreate standard indexes")

    print(f"  Found {len(indexes)} indexes")
    for idx_name, sql in indexes:
        print(f"    - {idx_name}")

    # Step 3: Drop existing indexes
    print("\n[3/10] Dropping indexes for faster bulk update...")

    for idx_name, _ in indexes:
        try:
            print(f"  Dropping {idx_name}...")
            conn.execute(f"DROP INDEX IF EXISTS {idx_name}")
        except Exception as e:
            print(f"  Warning: Could not drop {idx_name}: {e}")

    # Step 4-5: Create decoded table using DuckDB's built-in url_decode()
    print("\n[4/10] Creating temporary table with decoded data...")
    print("  Note: Using DuckDB's built-in url_decode() function for optimal performance")

    conn.execute("DROP TABLE IF EXISTS rdf_triples_decoded")

    print("\n[5/10] Decoding all URLs (this may take a few minutes)...")

    conn.execute("""
        CREATE TABLE rdf_triples_decoded AS
        SELECT
            url_decode(subj) as subj,
            url_decode(pred) as pred,
            url_decode(obj) as obj
        FROM rdf_triples
    """)

    print("  ✓ All URLs decoded and table created")

    # Step 6: Validation
    print("\n[6/10] Validating decoded data...")

    decoded_count = conn.execute("SELECT COUNT(*) FROM rdf_triples_decoded").fetchone()[0]
    print(f"  Original table: {total_rows:,} rows")
    print(f"  Decoded table: {decoded_count:,} rows")

    if decoded_count != total_rows:
        print(f"\n✗ ERROR: Row count mismatch! Rolling back...")
        conn.execute("DROP TABLE IF EXISTS rdf_triples_decoded")
        return 1

    print("  ✓ Row counts match")

    # Check for remaining URL encoding
    remaining_query = f"""
        SELECT COUNT(*)
        FROM rdf_triples_decoded
        WHERE {has_url_encoding_where_clause('subj')}
           OR {has_url_encoding_where_clause('pred')}
           OR {has_url_encoding_where_clause('obj')}
    """
    remaining_result = conn.execute(remaining_query).fetchone()

    remaining = remaining_result[0]
    print(f"  Remaining URL-encoded rows: {remaining:,}")

    if remaining > 0:
        print(f"  ⚠ WARNING: Some URL encoding remains (might be false positives)")
    else:
        print("  ✓ No URL encoding detected in decoded table")

    # Step 7: Swap tables
    print("\n[7/10] Swapping tables...")

    try:
        conn.execute("BEGIN TRANSACTION")
        conn.execute("DROP TABLE rdf_triples")
        conn.execute("ALTER TABLE rdf_triples_decoded RENAME TO rdf_triples")
        conn.execute("COMMIT")
        print("  ✓ Tables swapped successfully")
    except Exception as e:
        print(f"  ✗ ERROR during swap: {e}")
        conn.execute("ROLLBACK")
        return 1

    # Step 8: Recreate indexes
    print("\n[8/10] Recreating indexes...")

    # Always create the standard indexes
    print("  Creating idx_rdf_triples_subj...")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_rdf_triples_subj ON rdf_triples(subj)")

    print("  Creating idx_rdf_triples_pred...")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_rdf_triples_pred ON rdf_triples(pred)")

    # Check if we had composite indexes and recreate them
    index_names = [idx_name for idx_name, _ in indexes]

    if any('pred_obj' in name.lower() for name in index_names):
        print("  Creating rdf_triples_pred_obj_idx...")
        conn.execute("CREATE INDEX IF NOT EXISTS rdf_triples_pred_obj_idx ON rdf_triples(pred, obj)")

    if any('subj_pred' in name.lower() for name in index_names):
        print("  Creating rdf_triples_subj_pred_idx...")
        conn.execute("CREATE INDEX IF NOT EXISTS rdf_triples_subj_pred_idx ON rdf_triples(subj, pred)")

    print("  ✓ Indexes recreated")

    # Step 9: Analyze table for query optimization
    print("\n[9/10] Analyzing table statistics...")
    try:
        conn.execute("ANALYZE rdf_triples")
        print("  ✓ Table analyzed")
    except Exception as e:
        print(f"  Warning: Could not analyze table: {e}")

    # Step 10: Final validation
    print("\n[10/10] Final validation...")

    final_query = f"""
        SELECT
            COUNT(*) as total_rows,
            SUM(CASE WHEN {has_url_encoding_where_clause('subj')} THEN 1 ELSE 0 END) as subj_encoded,
            SUM(CASE WHEN {has_url_encoding_where_clause('pred')} THEN 1 ELSE 0 END) as pred_encoded,
            SUM(CASE WHEN {has_url_encoding_where_clause('obj')} THEN 1 ELSE 0 END) as obj_encoded
        FROM rdf_triples
    """
    final_stats = conn.execute(final_query).fetchone()

    print("\n" + "=" * 60)
    print("Final Statistics")
    print("=" * 60)
    print(f"  Total rows: {final_stats[0]:,}")
    print(f"  Rows with URL encoding remaining:")
    print(f"    - subj: {final_stats[1]:,}")
    print(f"    - pred: {final_stats[2]:,}")
    print(f"    - obj: {final_stats[3]:,}")

    print("\n" + "=" * 60)
    print("✓ Migration completed successfully!")
    print("=" * 60)

    conn.close()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n✗ ERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
