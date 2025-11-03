-- Populate data_object_submissions table from data_objects
-- This extracts unique submission URIs and their temporal data

INSERT INTO data_object_submissions (subject, hasStartTime, hasEndTime)
SELECT
    wasSubmittedBy as subject,
    MIN(submission_start_time) as hasStartTime,
    MAX(submission_end_time) as hasEndTime
FROM data_objects
WHERE wasSubmittedBy IS NOT NULL
GROUP BY wasSubmittedBy
ON CONFLICT (subject) DO UPDATE
SET
    hasStartTime = EXCLUDED.hasStartTime,
    hasEndTime = EXCLUDED.hasEndTime;
