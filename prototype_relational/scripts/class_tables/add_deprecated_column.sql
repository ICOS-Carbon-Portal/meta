-- Add 'deprecated' column to ct_static_objects and populate it.
-- A static object is deprecated if it is the target of is_next_version_of
-- from any other static object or plain collection.

ALTER TABLE ct_static_objects ADD COLUMN IF NOT EXISTS deprecated BOOLEAN DEFAULT false;

UPDATE ct_static_objects
SET deprecated = true
WHERE id IN (
    SELECT UNNEST(is_next_version_of)
    FROM ct_static_objects
    WHERE is_next_version_of IS NOT NULL
    UNION
    SELECT is_next_version_of
    FROM ct_plain_collections
    WHERE is_next_version_of IS NOT NULL
);
