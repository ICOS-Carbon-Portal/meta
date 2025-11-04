-- Find all subjects that exist in both data_object_submissions and data_object_acquisitions
-- This query returns the list of subjects that appear in both tables

SELECT dos.subject
FROM data_object_submissions dos
INNER JOIN data_object_acquisitions doa ON dos.subject = doa.subject
ORDER BY dos.subject;
