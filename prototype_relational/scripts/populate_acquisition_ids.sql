BEGIN;

-- First, check if all subjects have one of the expected prefixes
DO $$
DECLARE
    invalid_count INTEGER;
    invalid_subjects TEXT;
    filtered_count INTEGER;
BEGIN
    SELECT COUNT(*), string_agg(subject, ', ')
    INTO invalid_count, invalid_subjects
    FROM data_object_acquisitions
    WHERE NOT subject LIKE 'http://meta.icos-cp.eu/resources/acq_%'
      AND NOT subject LIKE 'https://meta.fieldsites.se/resources/acq_%';

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Found % subject(s) without expected prefix: %',
            invalid_count, invalid_subjects;
    END IF;

    -- Report how many entries will be filtered out
    SELECT COUNT(*) INTO filtered_count
    FROM data_object_acquisitions
    WHERE subject LIKE 'https://meta.fieldsites.se/resources/acq_%';

    RAISE NOTICE 'Filtering out % fieldsites entries', filtered_count;
END $$;

-- Update the id field only for ICOS entries
UPDATE data_object_acquisitions
SET id = REPLACE(subject, 'http://meta.icos-cp.eu/resources/acq_', '')
WHERE subject LIKE 'http://meta.icos-cp.eu/resources/acq_%';

-- Report how many rows were updated
DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO updated_count FROM data_object_acquisitions WHERE id IS NOT NULL;
    RAISE NOTICE 'Successfully populated % ICOS acquisition id(s)', updated_count;
END $$;

-- Check for data_objects referencing fieldsites acquisitions
DO $$
DECLARE
    fieldsites_refs INTEGER;
BEGIN
    SELECT COUNT(*) INTO fieldsites_refs
    FROM data_objects
    WHERE wasAcquiredBy LIKE 'https://meta.fieldsites.se/resources/acq_%';

    IF fieldsites_refs > 0 THEN
        RAISE NOTICE 'Warning: Found % data_objects referencing fieldsites acquisitions. These will be skipped and may need manual handling.', fieldsites_refs;
    END IF;
END $$;

-- Drop the existing foreign key constraint on wasAcquiredBy
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
      AND con.conname LIKE '%wasacquiredby%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE data_objects DROP CONSTRAINT %I', constraint_name);
        RAISE NOTICE 'Dropped constraint: %', constraint_name;
    ELSE
        RAISE NOTICE 'No existing wasAcquiredBy constraint found';
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
    WHERE wasAcquiredBy IS NOT NULL;

    SELECT COUNT(*) INTO icos_refs
    FROM data_objects
    WHERE wasAcquiredBy LIKE 'http://meta.icos-cp.eu/resources/acq_%';

    SELECT COUNT(*) INTO icos_with_id
    FROM data_objects dobj
    INNER JOIN data_object_acquisitions doa ON dobj.wasAcquiredBy = doa.subject
    WHERE doa.id IS NOT NULL;

    RAISE NOTICE 'Total wasAcquiredBy references: %, ICOS references: %, ICOS with populated id: %',
        total_refs, icos_refs, icos_with_id;
END $$;

-- Update wasAcquiredBy from subject to id for ICOS acquisitions that have an id
UPDATE data_objects dobj
SET wasAcquiredBy = doa.id
FROM data_object_acquisitions doa
WHERE dobj.wasAcquiredBy = doa.subject
  AND doa.id IS NOT NULL
  AND doa.subject LIKE 'http://meta.icos-cp.eu/resources/acq_%';

-- Report update results
DO $$
DECLARE
    updated_count INTEGER;
    remaining_subject_refs INTEGER;
BEGIN
    -- Count rows that now have id-based references (no http prefix)
    SELECT COUNT(*) INTO updated_count
    FROM data_objects dobj
    INNER JOIN data_object_acquisitions doa ON dobj.wasAcquiredBy = doa.id
    WHERE doa.id IS NOT NULL;

    -- Count rows still referencing subjects (http/https prefix)
    SELECT COUNT(*) INTO remaining_subject_refs
    FROM data_objects
    WHERE wasAcquiredBy LIKE 'http%';

    RAISE NOTICE 'Successfully migrated % references to use acquisition ids', updated_count;
    RAISE NOTICE 'Remaining subject-based references (skipped): %', remaining_subject_refs;
END $$;

-- Check for any wasAcquiredBy values that would violate the foreign key
DO $$
DECLARE
    invalid_refs INTEGER;
    sample_invalid TEXT;
BEGIN
    SELECT COUNT(*), MIN(wasAcquiredBy)
    INTO invalid_refs, sample_invalid
    FROM data_objects
    WHERE wasAcquiredBy IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM data_object_acquisitions doa
          WHERE doa.id = data_objects.wasAcquiredBy
      );

    IF invalid_refs > 0 THEN
        RAISE WARNING '% wasAcquiredBy values do not exist in data_object_acquisitions.id (example: %)',
            invalid_refs, sample_invalid;
        RAISE WARNING 'Foreign key constraint will NOT be created due to invalid references';
    ELSE
        -- Add new foreign key constraint referencing id column
        ALTER TABLE data_objects
        ADD CONSTRAINT data_objects_wasacquiredby_fkey
        FOREIGN KEY (wasAcquiredBy) REFERENCES data_object_acquisitions(id);

        RAISE NOTICE 'Successfully added new foreign key constraint for wasAcquiredBy';
    END IF;
END $$;

COMMIT;
