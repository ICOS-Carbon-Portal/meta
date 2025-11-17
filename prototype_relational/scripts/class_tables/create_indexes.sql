-- Generated SQL for class-based tables (INDEXES)
-- Source: class_predicates_analysis.json
-- Run this after creating tables with class_tables/create_class_tables.sql

-- ======================================================================
-- INDEXES
-- ======================================================================

-- Indexes for ct_spatial_coverages
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_as_geo_json ON ct_spatial_coverages(as_geo_json);
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_has_eastern_bound ON ct_spatial_coverages(has_eastern_bound);
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_has_northern_bound ON ct_spatial_coverages(has_northern_bound);
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_has_southern_bound ON ct_spatial_coverages(has_southern_bound);
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_has_western_bound ON ct_spatial_coverages(has_western_bound);
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_has_latitude ON ct_spatial_coverages(has_latitude);
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_has_longitude ON ct_spatial_coverages(has_longitude);

-- Indexes for ct_organizations
CREATE INDEX IF NOT EXISTS idx_ct_organizations_has_name ON ct_organizations(has_name);

-- Indexes for ct_data_submissions
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_was_associated_with ON ct_data_submissions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_ended_at_time ON ct_data_submissions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_started_at_time ON ct_data_submissions(started_at_time);

-- Indexes for ct_data_objects
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_submitted_by ON ct_data_objects(was_submitted_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_spatial_coverage ON ct_data_objects(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_name ON ct_data_objects(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_object_spec ON ct_data_objects(has_object_spec);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_sha256sum ON ct_data_objects(has_sha256sum);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_size_in_bytes ON ct_data_objects(has_size_in_bytes);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by ON ct_data_objects(was_acquired_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_end_time ON ct_data_objects(has_end_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_start_time ON ct_data_objects(has_start_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by_was_performed_with ON ct_data_objects(was_acquired_by_was_performed_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by_ended_at_time ON ct_data_objects(was_acquired_by_ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by_started_at_time ON ct_data_objects(was_acquired_by_started_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by_was_associated_with ON ct_data_objects(was_acquired_by_was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_produced_by_has_end_time ON ct_data_objects(was_produced_by_has_end_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_produced_by_was_performed_by ON ct_data_objects(was_produced_by_was_performed_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_actual_variable_label ON ct_data_objects(has_actual_variable_label);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_actual_variable_has_max_value ON ct_data_objects(has_actual_variable_has_max_value);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_actual_variable_has_min_value ON ct_data_objects(has_actual_variable_has_min_value);

-- Indexes for ct_persons
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership ON ct_persons(has_membership);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_first_name ON ct_persons(has_first_name);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_last_name ON ct_persons(has_last_name);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership_label ON ct_persons(has_membership_label);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership_has_role ON ct_persons(has_membership_has_role);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership_at_organization ON ct_persons(has_membership_at_organization);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership_has_start_time ON ct_persons(has_membership_has_start_time);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership_has_end_time ON ct_persons(has_membership_has_end_time);

-- Indexes for ct_collections
CREATE INDEX IF NOT EXISTS idx_ct_collections_see_also ON ct_collections(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_collections_creator ON ct_collections(creator);
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_part ON ct_collections(has_part);
CREATE INDEX IF NOT EXISTS idx_ct_collections_is_next_version_of ON ct_collections(is_next_version_of);
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_spatial_coverage ON ct_collections(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_collections_title ON ct_collections(title);
CREATE INDEX IF NOT EXISTS idx_ct_collections_description ON ct_collections(description);

-- Indexes for ct_document_objects
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_creator ON ct_document_objects(creator);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_was_submitted_by ON ct_document_objects(was_submitted_by);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_has_name ON ct_document_objects(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_has_sha256sum ON ct_document_objects(has_sha256sum);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_has_size_in_bytes ON ct_document_objects(has_size_in_bytes);

-- Indexes for ct_value_types
CREATE INDEX IF NOT EXISTS idx_ct_value_types_label ON ct_value_types(label);
CREATE INDEX IF NOT EXISTS idx_ct_value_types_has_quantity_kind_label ON ct_value_types(has_quantity_kind_label);

-- Indexes for ct_plain_collections
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_has_part ON ct_plain_collections(has_part);
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_is_next_version_of ON ct_plain_collections(is_next_version_of);

-- Indexes for ct_ecosystem_types
CREATE INDEX IF NOT EXISTS idx_ct_ecosystem_types_label ON ct_ecosystem_types(label);

-- Indexes for ct_value_formats
CREATE INDEX IF NOT EXISTS idx_ct_value_formats_label ON ct_value_formats(label);

-- Indexes for ct_data_themes
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_has_icon ON ct_data_themes(has_icon);
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_label ON ct_data_themes(label);

-- Indexes for ct_thematic_centers
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_data_theme ON ct_thematic_centers(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_name ON ct_thematic_centers(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_label ON ct_thematic_centers(label);

-- Indexes for ct_central_facilities
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_has_name ON ct_central_facilities(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_label ON ct_central_facilities(label);
