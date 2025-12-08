-- Fix URL-Encoded Characters in ct_projects IDs
-- ==========================================
-- Problem: Some ct_projects.id values contain URL-encoded characters like %20
-- Solution: Decode all URL-encoded characters to their actual values
--
-- Affected tables:
-- - ct_projects (primary table)
-- - ct_object_specs (child table with foreign key reference)

BEGIN;

-- Create URL decode function
-- This function decodes ALL percent-encoded characters (e.g., %20 → space, %2F → /, etc.)
CREATE OR REPLACE FUNCTION url_decode(input TEXT) RETURNS TEXT AS $$
DECLARE
    result TEXT := input;
    matches TEXT[];
    hex_digits TEXT;
    decoded_byte BYTEA;
BEGIN
    -- Loop through all %XX patterns
    LOOP
        matches := regexp_match(result, '%([0-9A-Fa-f]{2})');
        EXIT WHEN matches IS NULL;

        hex_digits := matches[1];
        -- Decode hex to bytea, then convert to text
        decoded_byte := decode(hex_digits, 'hex');
        -- Replace first occurrence (case-insensitive)
        result := regexp_replace(result, '%' || hex_digits, convert_from(decoded_byte, 'UTF8'), 'i');
    END LOOP;

    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Report current state before changes
DO $$
DECLARE
    projects_count INTEGER;
    object_specs_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO projects_count
    FROM ct_projects
    WHERE id ~ '%[0-9A-Fa-f][0-9A-Fa-f]';

    SELECT COUNT(*) INTO object_specs_count
    FROM ct_object_specs
    WHERE has_associated_project ~ '%[0-9A-Fa-f][0-9A-Fa-f]';

    RAISE NOTICE 'Rows to be updated:';
    RAISE NOTICE '  ct_projects: % rows with URL encoding', projects_count;
    RAISE NOTICE '  ct_object_specs.has_associated_project: % rows with URL encoding', object_specs_count;
END $$;

-- ============================================================================
-- Fix ct_projects and its foreign key references
-- ============================================================================

-- Drop foreign key constraint to allow updates
ALTER TABLE ct_object_specs DROP CONSTRAINT IF EXISTS fk_ct_object_specs_has_associated_project;

-- Update parent table with full URL decoding
-- IMPORTANT: Must update BOTH id and rdf_subject to maintain constraint
UPDATE ct_projects
SET
    id = url_decode(id),
    rdf_subject = url_decode(rdf_subject)
WHERE id ~ '%[0-9A-Fa-f][0-9A-Fa-f]';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE '';
    RAISE NOTICE 'Updated ct_projects: % rows', updated_count;
END $$;

-- Update child table to match
UPDATE ct_object_specs
SET has_associated_project = url_decode(has_associated_project)
WHERE has_associated_project ~ '%[0-9A-Fa-f][0-9A-Fa-f]';

DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RAISE NOTICE 'Updated ct_object_specs.has_associated_project: % rows', updated_count;
END $$;

-- Recreate foreign key constraint
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_associated_project
    FOREIGN KEY (has_associated_project) REFERENCES ct_projects(id);

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE 'Foreign key constraint recreated successfully';
END $$;

-- ============================================================================
-- Validation: Check that all constraints are still satisfied
-- ============================================================================

DO $$
DECLARE
    projects_violations INTEGER;
    projects_remaining INTEGER;
    object_specs_remaining INTEGER;
BEGIN
    -- Check for constraint violations (prefix || id should equal rdf_subject)
    SELECT COUNT(*) INTO projects_violations
    FROM ct_projects
    WHERE prefix || id != rdf_subject;

    -- Check for any remaining URL encoding
    SELECT COUNT(*) INTO projects_remaining
    FROM ct_projects
    WHERE id ~ '%[0-9A-Fa-f][0-9A-Fa-f]';

    SELECT COUNT(*) INTO object_specs_remaining
    FROM ct_object_specs
    WHERE has_associated_project ~ '%[0-9A-Fa-f][0-9A-Fa-f]';

    RAISE NOTICE '';
    RAISE NOTICE 'Validation Results:';
    RAISE NOTICE '  ct_projects constraint violations (prefix || id != rdf_subject): %', projects_violations;
    RAISE NOTICE '';
    RAISE NOTICE 'Remaining URL encoding:';
    RAISE NOTICE '  ct_projects.id: %', projects_remaining;
    RAISE NOTICE '  ct_object_specs.has_associated_project: %', object_specs_remaining;

    IF projects_violations > 0 THEN
        RAISE EXCEPTION 'Constraint violations detected! Rolling back...';
    END IF;

    IF projects_remaining > 0 OR object_specs_remaining > 0 THEN
        RAISE EXCEPTION 'Some URL encoding was not decoded! Rolling back...';
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE 'All validations passed!';
END $$;

-- Drop the temporary function
DROP FUNCTION url_decode(TEXT);

COMMIT;

-- Final success message (will only print if COMMIT succeeds)
DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration completed successfully!';
    RAISE NOTICE '========================================';
END $$;
