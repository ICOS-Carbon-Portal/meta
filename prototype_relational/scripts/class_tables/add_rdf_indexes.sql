-- Composite indexes on rdf_triples for optimized class table population
-- Run this BEFORE populating class tables for 5-10x speedup

-- These indexes dramatically speed up the WHERE pred = 'rdf:type' AND obj = '<class>' subqueries
-- that appear in every INSERT statement in populate_class_tables.sql

BEGIN;

-- Composite index for class filtering (pred, obj)
-- Optimizes: SELECT subj WHERE pred = 'rdf:type' AND obj = '<class_uri>'
CREATE INDEX IF NOT EXISTS rdf_triples_pred_obj_idx ON rdf_triples(pred, obj);

-- Composite index for subject-predicate lookups (subj, pred)
-- Optimizes: GROUP BY subj with CASE WHEN pred = '<predicate>' aggregations
CREATE INDEX IF NOT EXISTS rdf_triples_subj_pred_idx ON rdf_triples(subj, pred);

COMMIT;

-- Analyze the table to update statistics after index creation
ANALYZE rdf_triples;
