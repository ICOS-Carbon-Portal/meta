#!/bin/bash
#
# Convert all class tables from UNLOGGED to LOGGED
# Run this after population is complete to make data crash-safe
#
# Usage: ./convert_to_logged.sh

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

echo "Converting UNLOGGED tables to LOGGED..."
echo ""

# Get list of all ct_* tables
TABLES=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT tablename
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename LIKE 'ct_%'
    ORDER BY tablename;
")

if [ -z "$TABLES" ]; then
    echo "No class tables found (ct_* pattern)"
    exit 1
fi

TABLE_COUNT=$(echo "$TABLES" | wc -l)
echo "Found $TABLE_COUNT tables to convert:"
echo "$TABLES" | sed 's/^/  - /'
echo ""

# Convert each table
COUNT=0
for TABLE in $TABLES; do
    COUNT=$((COUNT + 1))
    echo "[$COUNT/$TABLE_COUNT] Converting $TABLE..."
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
        "ALTER TABLE $TABLE SET LOGGED;" 2>&1 | grep -v "^ALTER TABLE$" || true
done

echo ""
echo "✓ All $TABLE_COUNT tables converted to LOGGED mode"
echo "  Tables are now crash-safe and will persist through database restarts"
