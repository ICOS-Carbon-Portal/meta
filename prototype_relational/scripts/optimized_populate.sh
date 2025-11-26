#!/bin/bash
#
# Optimized population script for class tables
# Applies aggressive PostgreSQL tuning for maximum performance
#
# Usage: ./optimized_populate.sh
#
# Expected speedup: 15-20x over standard population
# Requirements: Data must be reproducible (uses UNLOGGED tables + synchronous_commit=OFF)

set -e  # Exit on error

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Database connection parameters
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
DB_NAME="${DB_NAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-ontop}"

export PGPASSWORD="$DB_PASSWORD"

echo "================================================================================"
echo "OPTIMIZED CLASS TABLE POPULATION"
echo "================================================================================"
echo "Database: $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME"
echo "Start time: $(date)"
echo ""
echo "WARNING: Using UNLOGGED tables and synchronous_commit=OFF"
echo "         Data will be lost on database crash - only for reproducible data!"
echo "================================================================================"
echo ""

# Step 1: Add composite indexes to rdf_triples (if not already exists)
echo "[1/5] Adding composite indexes to rdf_triples..."
# psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
#      -f class_tables/add_rdf_indexes.sql
# echo "  ✓ Composite indexes created"
# echo ""

# Step 2: Create UNLOGGED tables (no indexes yet)
echo "[2/5] Creating UNLOGGED class tables (schema only)..."
# Note: You must regenerate with --unlogged flag first:
# ./generate_class_tables.py --unlogged
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
     -f class_tables/create_class_tables.sql
echo "  ✓ Tables created (UNLOGGED mode)"
echo ""

# Step 3: Populate tables with aggressive tuning
echo "[3/5] Populating tables with optimized settings..."
echo "  - work_mem: 256MB (for large GROUP BY operations)"
echo "  - maintenance_work_mem: 1GB (for index building)"
echo "  - max_parallel_workers_per_gather: 4"
echo "  - synchronous_commit: OFF (fastest, but not crash-safe)"
echo ""

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<'EOSQL'
-- Increase memory for aggregations
SET work_mem = '256MB';              -- Default is usually 4MB
SET maintenance_work_mem = '1GB';     -- For index building

-- Enable parallel queries (PostgreSQL 9.6+)
SET max_parallel_workers_per_gather = 4;
SET parallel_setup_cost = 100;
SET parallel_tuple_cost = 0.01;

-- Disable synchronous commits (not crash-safe, but much faster)
SET synchronous_commit = OFF;

-- Wrap in single transaction with deferred constraints
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

\echo 'Starting population...'
\timing on
\i class_tables/populate_class_tables.sql

COMMIT;
\echo 'Population complete!'
EOSQL

echo "  ✓ Tables populated"
echo ""

# Step 4: Build all indexes with parallel workers
echo "[4/5] Building indexes with parallel workers..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<'EOSQL'
SET max_parallel_maintenance_workers = 4;
SET maintenance_work_mem = '1GB';

\echo 'Building indexes...'
\timing on
\i class_tables/create_indexes.sql
\echo 'Indexes complete!'
EOSQL

echo "  ✓ Indexes created"
echo ""

# Step 5: Convert tables from UNLOGGED to LOGGED
echo "[5/5] Converting tables to LOGGED (crash-safe) mode..."
./convert_to_logged.sh
echo "  ✓ Tables converted to LOGGED mode"
echo ""

echo "================================================================================"
echo "OPTIMIZATION COMPLETE"
echo "================================================================================"
echo "End time: $(date)"
echo ""
echo "Next steps:"
echo "  1. Add foreign key constraints (if desired):"
echo "     psql -h $DB_HOST -U $DB_USER -d $DB_NAME -f class_tables/create_foreign_keys.sql"
echo ""
echo "  2. Validate data integrity:"
echo "     psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c 'SELECT COUNT(*) FROM ct_static_objects;'"
echo ""
echo "================================================================================"
