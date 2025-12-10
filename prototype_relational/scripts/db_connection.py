#!/usr/bin/env python3
"""
Centralized database connection utility for DuckDB.
Provides drop-in replacement for psycopg2 connection pattern.
"""
import duckdb
import os
from pathlib import Path

# Default database path
DEFAULT_DB_PATH = os.path.join(
    os.path.dirname(__file__),
    'data',
    'rdfsql.duckdb'
)

def get_connection(db_path=None):
    """
    Create and return a DuckDB database connection.

    Args:
        db_path: Path to DuckDB file (default: data/rdfsql.duckdb)

    Returns:
        DuckDB connection object with cursor() method
    """
    if db_path is None:
        db_path = DEFAULT_DB_PATH

    # Ensure parent directory exists
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)

    # DuckDB connection
    conn = duckdb.connect(db_path)

    # Enable better PostgreSQL compatibility
    conn.execute("SET enable_progress_bar = false")

    return conn

def execute_sql_file(conn, path):
    """
    Execute SQL from a file.

    Args:
        conn: DuckDB connection
        path: Path to SQL file
    """
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
        # Split on semicolons and execute each statement
        statements = [s.strip() for s in content.split(';') if s.strip()]
        for statement in statements:
            if statement and not statement.startswith('--'):
                conn.execute(statement)
