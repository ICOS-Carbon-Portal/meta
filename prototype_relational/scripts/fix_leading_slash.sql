-- Fix Leading Slash Issue in Class Tables
-- ==========================================
-- Problem: The id column incorrectly contains a leading '/' that should be in the prefix column
-- Affected tables: ct_projects, ct_data_themes, ct_collections (partial)
--
-- This script moves the leading '/' from the id column to the prefix column
-- to maintain the constraint: prefix || id = rdf_subject
--
-- IMPORTANT: This also updates foreign key references in child tables

BEGIN;

-- Drop foreign key constraints that will be affected by the updates
-- We'll recreate them at the end after all updates are complete
ALTER TABLE ct_object_specs DROP CONSTRAINT IF EXISTS fk_ct_object_specs_has_associated_project;
ALTER TABLE ct_object_specs DROP CONSTRAINT IF EXISTS fk_ct_object_specs_has_data_theme;
ALTER TABLE ct_thematic_centers DROP CONSTRAINT IF EXISTS fk_ct_thematic_centers_has_data_theme;
ALTER TABLE ct_collections DROP CONSTRAINT IF EXISTS fk_ct_collections_has_spatial_coverage;
ALTER TABLE ct_static_objects DROP CONSTRAINT IF EXISTS fk_ct_static_objects_has_spatial_coverage;
ALTER TABLE ct_stations DROP CONSTRAINT IF EXISTS fk_ct_stations_has_spatial_coverage;

-- Report current state before changes
DO $$
DECLARE
    projects_count INTEGER;
    themes_count INTEGER;
    collections_count INTEGER;
    object_specs_project_count INTEGER;
    object_specs_theme_count INTEGER;
    thematic_centers_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO projects_count FROM ct_projects WHERE id LIKE '/%';
    SELECT COUNT(*) INTO themes_count FROM ct_data_themes WHERE id LIKE '/%';
    SELECT COUNT(*) INTO collections_count FROM ct_collections WHERE has_spatial_coverage LIKE '/%';
    SELECT COUNT(*) INTO object_specs_project_count FROM ct_object_specs WHERE has_associated_project LIKE '/%';
    SELECT COUNT(*) INTO object_specs_theme_count FROM ct_object_specs WHERE has_data_theme LIKE '/%';
    SELECT COUNT(*) INTO thematic_centers_count FROM ct_thematic_centers WHERE has_data_theme LIKE '/%';

    RAISE NOTICE 'Rows to be updated:';
    RAISE NOTICE '  ct_projects: % rows with id starting with /', projects_count;
    RAISE NOTICE '  ct_data_themes: % rows with id starting with /', themes_count;
    RAISE NOTICE '  ct_collections: % rows with has_spatial_coverage starting with /', collections_count;
    RAISE NOTICE '';
    RAISE NOTICE 'Foreign key references to be updated:';
    RAISE NOTICE '  ct_object_specs.has_associated_project: % rows', object_specs_project_count;
    RAISE NOTICE '  ct_object_specs.has_data_theme: % rows', object_specs_theme_count;
    RAISE NOTICE '  ct_thematic_centers.has_data_theme: % rows', thematic_centers_count;
END $$;

-- ============================================================================
-- Fix ct_projects and its foreign key references
-- ============================================================================

-- Update ct_projects first (constraints are deferred)
-- Move the leading '/' from id to prefix
UPDATE ct_projects
SET
    prefix = prefix || '/',
    id = SUBSTRING(id FROM 2)
WHERE id LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE '';
    RAISE NOTICE 'Updated ct_projects: % rows', updated_count;
END $$;

-- Now update the foreign key references in child tables to match
UPDATE ct_object_specs
SET has_associated_project = SUBSTRING(has_associated_project FROM 2)
WHERE has_associated_project LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_object_specs.has_associated_project: % rows', updated_count;
END $$;

-- ============================================================================
-- Fix ct_data_themes and its foreign key references
-- ============================================================================

-- Update ct_data_themes first (constraints are deferred)
-- Move the leading '/' from id to prefix
UPDATE ct_data_themes
SET
    prefix = prefix || '/',
    id = SUBSTRING(id FROM 2)
WHERE id LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE '';
    RAISE NOTICE 'Updated ct_data_themes: % rows', updated_count;
END $$;

-- Now update the foreign key references in child tables to match
UPDATE ct_object_specs
SET has_data_theme = SUBSTRING(has_data_theme FROM 2)
WHERE has_data_theme LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_object_specs.has_data_theme: % rows', updated_count;
END $$;

UPDATE ct_thematic_centers
SET has_data_theme = SUBSTRING(has_data_theme FROM 2)
WHERE has_data_theme LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_thematic_centers.has_data_theme: % rows', updated_count;
END $$;

-- ============================================================================
-- Fix ct_spatial_coverages (latlonboxes only)
-- ============================================================================

-- Update ct_spatial_coverages for latlonboxes entries
UPDATE ct_spatial_coverages
SET
    prefix = prefix || '/',
    id = SUBSTRING(id FROM 2)
WHERE id LIKE '/%' AND prefix = 'http://meta.icos-cp.eu/resources/latlonboxes';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE '';
    RAISE NOTICE 'Updated ct_spatial_coverages (latlonboxes): % rows', updated_count;
END $$;

-- Now update the foreign key references in child tables to match
UPDATE ct_collections
SET has_spatial_coverage = SUBSTRING(has_spatial_coverage FROM 2)
WHERE has_spatial_coverage LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_collections.has_spatial_coverage: % rows', updated_count;
END $$;

UPDATE ct_static_objects
SET has_spatial_coverage = SUBSTRING(has_spatial_coverage FROM 2)
WHERE has_spatial_coverage LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_static_objects.has_spatial_coverage: % rows', updated_count;
END $$;

UPDATE ct_stations
SET has_spatial_coverage = SUBSTRING(has_spatial_coverage FROM 2)
WHERE has_spatial_coverage LIKE '/%';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_stations.has_spatial_coverage: % rows', updated_count;
END $$;

-- ============================================================================
-- Recreate foreign key constraints
-- ============================================================================

ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_associated_project
    FOREIGN KEY (has_associated_project) REFERENCES ct_projects(id);

ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_data_theme
    FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);

ALTER TABLE ct_thematic_centers ADD CONSTRAINT fk_ct_thematic_centers_has_data_theme
    FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);

ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage
    FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

ALTER TABLE ct_static_objects ADD CONSTRAINT fk_ct_static_objects_has_spatial_coverage
    FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_spatial_coverage
    FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE 'Foreign key constraints recreated successfully';
END $$;

-- ============================================================================
-- Validation: Check that all constraints are still satisfied
-- ============================================================================

DO $$
DECLARE
    projects_violations INTEGER;
    themes_violations INTEGER;
    spatial_violations INTEGER;
    projects_remaining INTEGER;
    themes_remaining INTEGER;
    spatial_remaining INTEGER;
    collections_remaining INTEGER;
    static_objects_remaining INTEGER;
    stations_remaining INTEGER;
    object_specs_project_remaining INTEGER;
    object_specs_theme_remaining INTEGER;
    thematic_centers_remaining INTEGER;
BEGIN
    -- Check for constraint violations
    SELECT COUNT(*) INTO projects_violations
    FROM ct_projects
    WHERE prefix || id != rdf_subject;

    SELECT COUNT(*) INTO themes_violations
    FROM ct_data_themes
    WHERE prefix || id != rdf_subject;

    SELECT COUNT(*) INTO spatial_violations
    FROM ct_spatial_coverages
    WHERE prefix || id != rdf_subject;

    -- Check for any remaining leading slashes in primary tables
    SELECT COUNT(*) INTO projects_remaining FROM ct_projects WHERE id LIKE '/%';
    SELECT COUNT(*) INTO themes_remaining FROM ct_data_themes WHERE id LIKE '/%';
    SELECT COUNT(*) INTO spatial_remaining FROM ct_spatial_coverages WHERE id LIKE '/%';

    -- Check for any remaining leading slashes in foreign key columns
    SELECT COUNT(*) INTO object_specs_project_remaining FROM ct_object_specs WHERE has_associated_project LIKE '/%';
    SELECT COUNT(*) INTO object_specs_theme_remaining FROM ct_object_specs WHERE has_data_theme LIKE '/%';
    SELECT COUNT(*) INTO thematic_centers_remaining FROM ct_thematic_centers WHERE has_data_theme LIKE '/%';
    SELECT COUNT(*) INTO collections_remaining FROM ct_collections WHERE has_spatial_coverage LIKE '/%';
    SELECT COUNT(*) INTO static_objects_remaining FROM ct_static_objects WHERE has_spatial_coverage LIKE '/%';
    SELECT COUNT(*) INTO stations_remaining FROM ct_stations WHERE has_spatial_coverage LIKE '/%';

    RAISE NOTICE '';
    RAISE NOTICE 'Validation Results:';
    RAISE NOTICE '  ct_projects constraint violations: %', projects_violations;
    RAISE NOTICE '  ct_data_themes constraint violations: %', themes_violations;
    RAISE NOTICE '  ct_spatial_coverages constraint violations: %', spatial_violations;
    RAISE NOTICE '';
    RAISE NOTICE 'Remaining leading slashes in primary tables:';
    RAISE NOTICE '  ct_projects.id: %', projects_remaining;
    RAISE NOTICE '  ct_data_themes.id: %', themes_remaining;
    RAISE NOTICE '  ct_spatial_coverages.id: %', spatial_remaining;
    RAISE NOTICE '';
    RAISE NOTICE 'Remaining leading slashes in foreign key columns:';
    RAISE NOTICE '  ct_object_specs.has_associated_project: %', object_specs_project_remaining;
    RAISE NOTICE '  ct_object_specs.has_data_theme: %', object_specs_theme_remaining;
    RAISE NOTICE '  ct_thematic_centers.has_data_theme: %', thematic_centers_remaining;
    RAISE NOTICE '  ct_collections.has_spatial_coverage: %', collections_remaining;
    RAISE NOTICE '  ct_static_objects.has_spatial_coverage: %', static_objects_remaining;
    RAISE NOTICE '  ct_stations.has_spatial_coverage: %', stations_remaining;

    IF projects_violations > 0 OR themes_violations > 0 OR spatial_violations > 0 THEN
        RAISE EXCEPTION 'Constraint violations detected! Rolling back...';
    END IF;

    IF projects_remaining > 0 OR themes_remaining > 0 OR spatial_remaining > 0 THEN
        RAISE EXCEPTION 'Some leading slashes were not fixed in primary tables! Rolling back...';
    END IF;

    IF object_specs_project_remaining > 0 OR object_specs_theme_remaining > 0 OR thematic_centers_remaining > 0 OR collections_remaining > 0 OR static_objects_remaining > 0 OR stations_remaining > 0 THEN
        RAISE EXCEPTION 'Some leading slashes were not fixed in foreign key columns! Rolling back...';
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE 'All validations passed!';
END $$;

COMMIT;

-- Final success message (will only print if COMMIT succeeds)
DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration completed successfully!';
    RAISE NOTICE '========================================';
END $$;
