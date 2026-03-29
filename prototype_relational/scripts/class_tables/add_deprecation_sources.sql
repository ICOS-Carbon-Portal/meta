-- Add 'deprecated_by' column to each type of object that can be the target
-- of is_next_version_of, and populate it with the id of the object that
-- supersedes it.
--
-- Deprecatable types:
--   ct_static_objects  - targeted by ct_static_objects and ct_plain_collections
--   ct_collections     - targeted by ct_collections

-- ======================================================================
-- ct_static_objects
-- ======================================================================

ALTER TABLE ct_static_objects ADD COLUMN IF NOT EXISTS deprecated_by TEXT;

-- Deprecated by another static object
UPDATE ct_static_objects AS target
SET deprecated_by = source.id
FROM (
    SELECT UNNEST(is_next_version_of) AS target_id, id
    FROM ct_static_objects
    WHERE is_next_version_of IS NOT NULL
) AS source
WHERE target.id = source.target_id;

-- Deprecated by a plain collection (overwrites only if not already set)
UPDATE ct_static_objects AS target
SET deprecated_by = source.id
FROM ct_plain_collections AS source
WHERE source.is_next_version_of IS NOT NULL
  AND target.id = source.is_next_version_of
  AND target.deprecated_by IS NULL;

-- ======================================================================
-- ct_collections
-- ======================================================================

ALTER TABLE ct_collections ADD COLUMN IF NOT EXISTS deprecated_by TEXT;

UPDATE ct_collections AS target
SET deprecated_by = source.id
FROM (
    SELECT UNNEST(is_next_version_of) AS target_id, id
    FROM ct_collections
    WHERE is_next_version_of IS NOT NULL
) AS source
WHERE target.id = source.target_id;
