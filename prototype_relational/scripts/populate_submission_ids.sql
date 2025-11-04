-- Populate id field in data_object_submissions from subject field
-- Removes the prefix 'http://meta.icos-cp.eu/resources/subm_'
-- Filters out entries with prefix 'https://meta.fieldsites.se/resources/subm_'
-- Errors if any entry doesn't have one of the expected prefixes

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
    FROM data_object_submissions
    WHERE NOT subject LIKE 'http://meta.icos-cp.eu/resources/subm_%'
      AND NOT subject LIKE 'https://meta.fieldsites.se/resources/subm_%';

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Found % subject(s) without expected prefix: %',
            invalid_count, invalid_subjects;
    END IF;

    -- Report how many entries will be filtered out
    SELECT COUNT(*) INTO filtered_count
    FROM data_object_submissions
    WHERE subject LIKE 'https://meta.fieldsites.se/resources/subm_%';

    RAISE NOTICE 'Filtering out % fieldsites entries', filtered_count;
END $$;

-- Update the id field only for ICOS entries
UPDATE data_object_submissions
SET id = REPLACE(subject, 'http://meta.icos-cp.eu/resources/subm_', '')
WHERE subject LIKE 'http://meta.icos-cp.eu/resources/subm_%';

-- Report how many rows were updated
DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO updated_count FROM data_object_submissions WHERE id IS NOT NULL;
    RAISE NOTICE 'Successfully populated % ICOS submission id(s)', updated_count;
END $$;

COMMIT;
