-- Test if we can drop the constraint
BEGIN;
SELECT 'Attempting to drop constraint...' as status;
ALTER TABLE ct_object_specs DROP CONSTRAINT IF EXISTS fk_ct_object_specs_has_associated_project;
SELECT 'Constraint dropped successfully' as status;
ROLLBACK;
