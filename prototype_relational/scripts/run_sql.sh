#!/usr/bin/env bash

# PGPASSWORD=ontop psql --host localhost --port 5432 -U postgres -v ON_ERROR_STOP=on -d postgres -a -f "$1"
duckdb < "$1"  data/rdfsql.duckdb
