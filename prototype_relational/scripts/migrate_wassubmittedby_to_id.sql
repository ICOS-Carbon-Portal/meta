-- Migrate wasSubmittedBy in data_objects to reference id column instead of subject
-- Only updates rows that reference ICOS submissions with populated ids
-- Skips fieldsites references

BEGIN;

-- Check for data_objects referencing fieldsites submissions
DO $$
DECLARE
    fieldsites_refs INTEGER;
BEGIN
    SELECT COUNT(*) INTO fieldsites_refs
    FROM data_objects
    WHERE wasSubmittedBy LIKE 'https://meta.fieldsites.se/resources/subm_%';

    IF fieldsites_refs > 0 THEN
        RAISE NOTICE 'Warning: Found % data_objects referencing fieldsites submissions. These will be skipped and may need manual handling.', fieldsites_refs;
    END IF;
END $$;

-- Drop the existing foreign key constraint on wasSubmittedBy
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    INNER JOIN pg_class rel ON rel.oid = con.conrelid
    INNER JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE rel.relname = 'data_objects'
      AND con.contype = 'f'
      AND con.conname LIKE '%wassubmittedby%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE data_objects DROP CONSTRAINT %I', constraint_name);
        RAISE NOTICE 'Dropped constraint: %', constraint_name;
    ELSE
        RAISE NOTICE 'No existing wasSubmittedBy constraint found';
    END IF;
END $$;

-- Report statistics before update
DO $$
DECLARE
    total_refs INTEGER;
    icos_refs INTEGER;
    icos_with_id INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_refs
    FROM data_objects
    WHERE wasSubmittedBy IS NOT NULL;

    SELECT COUNT(*) INTO icos_refs
    FROM data_objects
    WHERE wasSubmittedBy LIKE 'http://meta.icos-cp.eu/resources/subm_%';

    SELECT COUNT(*) INTO icos_with_id
    FROM data_objects dobj
    INNER JOIN data_object_submissions dos ON dobj.wasSubmittedBy = dos.subject
    WHERE dos.id IS NOT NULL;

    RAISE NOTICE 'Total wasSubmittedBy references: %, ICOS references: %, ICOS with populated id: %',
        total_refs, icos_refs, icos_with_id;
END $$;

-- Update wasSubmittedBy from subject to id for ICOS submissions that have an id
UPDATE data_objects dobj
SET wasSubmittedBy = dos.id
FROM data_object_submissions dos
WHERE dobj.wasSubmittedBy = dos.subject
  AND dos.id IS NOT NULL
  AND dos.subject LIKE 'http://meta.icos-cp.eu/resources/subm_%';

-- Report update results
DO $$
DECLARE
    updated_count INTEGER;
    remaining_subject_refs INTEGER;
BEGIN
    -- Count rows that now have id-based references (no http prefix)
    SELECT COUNT(*) INTO updated_count
    FROM data_objects dobj
    INNER JOIN data_object_submissions dos ON dobj.wasSubmittedBy = dos.id
    WHERE dos.id IS NOT NULL;

    -- Count rows still referencing subjects (http/https prefix)
    SELECT COUNT(*) INTO remaining_subject_refs
    FROM data_objects
    WHERE wasSubmittedBy LIKE 'http%';

    RAISE NOTICE 'Successfully migrated % references to use submission ids', updated_count;
    RAISE NOTICE 'Remaining subject-based references (skipped): %', remaining_subject_refs;
END $$;

COMMIT;

-- Add new foreign key constraint referencing id column
-- Note: This will fail if there are any wasSubmittedBy values that don't exist in data_object_submissions.id
ALTER TABLE data_objects
ADD CONSTRAINT data_objects_wassubmittedby_fkey
FOREIGN KEY (wasSubmittedBy) REFERENCES data_object_submissions(id);

COMMIT;
