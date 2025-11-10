BEGIN;

-- Check if all subjects have the expected ICOS prefix
DO $$
DECLARE
    invalid_count INTEGER;
    invalid_subjects TEXT;
    total_count INTEGER;
BEGIN
    -- Get total count of data objects
    SELECT COUNT(*) INTO total_count FROM data_objects;
    RAISE NOTICE 'Total data_objects entries: %', total_count;

    -- Check for subjects without the expected prefix
    SELECT COUNT(*) INTO invalid_count
    FROM data_objects
    WHERE NOT subject LIKE 'https://meta.icos-cp.eu/objects/%';

    -- Get sample of invalid subjects (up to 10)
    SELECT string_agg(subject, ', ')
    INTO invalid_subjects
    FROM (
        SELECT subject
        FROM data_objects
        WHERE NOT subject LIKE 'https://meta.icos-cp.eu/objects/%'
        ORDER BY subject
        LIMIT 10
    ) sample;

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Found % subject(s) without expected prefix "https://meta.icos-cp.eu/objects/". Examples: %',
            invalid_count, invalid_subjects;
    END IF;

    RAISE NOTICE 'All subjects have the expected ICOS prefix';
END $$;

-- Update the id field by extracting it from the subject
UPDATE data_objects
SET id = REPLACE(subject, 'https://meta.icos-cp.eu/objects/', '')
WHERE subject LIKE 'https://meta.icos-cp.eu/objects/%';

-- Report how many rows were updated
DO $$
DECLARE
    updated_count INTEGER;
    null_id_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO updated_count FROM data_objects WHERE id IS NOT NULL;
    SELECT COUNT(*) INTO null_id_count FROM data_objects WHERE id IS NULL;

    RAISE NOTICE 'Successfully populated % data_object id(s)', updated_count;

    IF null_id_count > 0 THEN
        RAISE WARNING 'Found % data_object(s) with NULL id', null_id_count;
    END IF;
END $$;

COMMIT;
