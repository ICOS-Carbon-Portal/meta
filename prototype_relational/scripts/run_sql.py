#!/usr/bin/env python3
"""
Execute SQL file using DuckDB.
Replacement for scripts/run_sql.sh
"""
import sys
sys.path.insert(0, '..')
from db_connection import get_connection, execute_sql_file

if len(sys.argv) < 2:
    print("Usage: run_sql.py <sql_file>")
    sys.exit(1)

conn = get_connection()
execute_sql_file(conn, sys.argv[1])
conn.commit()
print(f"Executed {sys.argv[1]} successfully")
