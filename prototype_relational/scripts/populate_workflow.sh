#!/usr/bin/env bash
set -e  # Exit on first error

DB_PATH="${DB_PATH:-data/rdfsql.duckdb}"

echo "============================================"
echo "Database Population Workflow"
echo "============================================"
echo ""

# Step 1: Create schema
echo "[1/6] Creating schema..."
./run_sql.sh class_tables/create_class_tables.sql
echo "✓ Schema created"
echo ""

# Step 2: Populate tables (FK checks disabled in SQL)
echo "[2/6] Populating tables (FK checks disabled)..."
./run_sql.sh class_tables/populate_class_tables.sql
echo "✓ Tables populated"
echo ""

# Step 3: Detect missing FK references
echo "[3/6] Detecting missing FK references..."
python fix_missing_fk_references.py --db-path "$DB_PATH" --report-only
echo ""

# Step 4: Generate fix SQL
echo "[4/6] Generating fix SQL..."
python fix_missing_fk_references.py --db-path "$DB_PATH" --output fix_fk.sql
echo "✓ fix_fk.sql generated"
echo ""

# Step 5: Apply fixes
if [ -f fix_fk.sql ]; then
    echo "[5/6] Applying FK fixes..."
    ./run_sql.sh fix_fk.sql
    echo "✓ Fixes applied"
else
    echo "[5/6] No fixes needed (fix_fk.sql not generated)"
fi
echo ""

# Step 6: Verify integrity
echo "[6/6] Verifying FK integrity..."
python fix_missing_fk_references.py --db-path "$DB_PATH" --report-only
echo ""

echo "============================================"
echo "Population workflow complete!"
echo "============================================"
