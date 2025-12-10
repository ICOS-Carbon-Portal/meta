-- Foreign key constraints for DuckDB
-- Source: class_predicates_analysis.json

-- ======================================================================
-- NOTE: FOREIGN KEYS ARE DEFINED INLINE
-- ======================================================================

-- For DuckDB compatibility, foreign key constraints are defined
-- inline within the CREATE TABLE statements in create_class_tables.sql

-- DuckDB does not support ALTER TABLE ADD CONSTRAINT for foreign keys.
-- All foreign keys must be defined during table creation.

-- Tables are created in dependency order to ensure referenced tables
-- exist before referencing tables are created.

-- Note: Array columns (multi-valued properties) do not have FK constraints
-- as neither PostgreSQL nor DuckDB support foreign key constraints on array columns.
