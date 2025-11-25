-- Generated SQL for foreign key constraints
-- Source: class_predicates_analysis.json
-- Total tables: 34

-- ======================================================================
-- FOREIGN KEY CONSTRAINTS
-- Note: Array columns (multi-valued properties) do not have FK constraints
-- PostgreSQL does not support foreign key constraints on array columns
-- ======================================================================

-- -- Foreign keys for ct_data_acquisitions
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_stations(id);

BEGIN;

-- Foreign keys for ct_collections
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);


-- Foreign keys for ct_data_productions
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_hosted_by FOREIGN KEY (was_hosted_by) REFERENCES ct_thematic_centers(id);

-- Foreign keys for ct_data_submissions
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_thematic_centers(id);

-- Foreign keys for ct_dataset_columns
ALTER TABLE ct_dataset_columns ADD CONSTRAINT fk_ct_dataset_columns_has_value_format FOREIGN KEY (has_value_format) REFERENCES ct_value_formats(id);
ALTER TABLE ct_dataset_columns ADD CONSTRAINT fk_ct_dataset_columns_has_value_type FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id);

-- Foreign keys for ct_dataset_variables
ALTER TABLE ct_dataset_variables ADD CONSTRAINT fk_ct_dataset_variables_has_value_type FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id);

-- Foreign keys for ct_fundings
ALTER TABLE ct_fundings ADD CONSTRAINT fk_ct_fundings_has_funder FOREIGN KEY (has_funder) REFERENCES ct_funders(id);

-- Foreign keys for ct_instruments
ALTER TABLE ct_instruments ADD CONSTRAINT fk_ct_instruments_has_vendor FOREIGN KEY (has_vendor) REFERENCES ct_organizations(id);
ALTER TABLE ct_instruments ADD CONSTRAINT fk_ct_instruments_has_instrument_owner FOREIGN KEY (has_instrument_owner) REFERENCES ct_organizations(id);

-- Foreign keys for ct_memberships
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_has_role FOREIGN KEY (has_role) REFERENCES ct_roles(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_organizations(id);

-- Foreign keys for ct_object_formats
ALTER TABLE ct_object_formats ADD CONSTRAINT fk_ct_object_formats_see_also FOREIGN KEY (see_also) REFERENCES ct_value_formats(id);

-- Foreign keys for ct_object_specs
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_encoding FOREIGN KEY (has_encoding) REFERENCES ct_object_encodings(id);
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_format FOREIGN KEY (has_format) REFERENCES ct_object_formats(id);
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_associated_project FOREIGN KEY (has_associated_project) REFERENCES ct_projects(id);
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_specific_dataset_type FOREIGN KEY (has_specific_dataset_type) REFERENCES ct_specific_dataset_types(id);
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_contains_dataset FOREIGN KEY (contains_dataset) REFERENCES ct_dataset_specs(id);

-- Foreign keys for ct_plain_collections
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_static_objects(id);

-- Foreign keys for ct_static_objects
ALTER TABLE ct_static_objects ADD CONSTRAINT fk_ct_static_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_static_objects ADD CONSTRAINT fk_ct_static_objects_was_acquired_by FOREIGN KEY (was_acquired_by) REFERENCES ct_data_acquisitions(id);
ALTER TABLE ct_static_objects ADD CONSTRAINT fk_ct_static_objects_was_produced_by FOREIGN KEY (was_produced_by) REFERENCES ct_data_productions(id);
ALTER TABLE ct_static_objects ADD CONSTRAINT fk_ct_static_objects_has_object_spec FOREIGN KEY (has_object_spec) REFERENCES ct_object_specs(id);
ALTER TABLE ct_static_objects ADD CONSTRAINT fk_ct_static_objects_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_stations
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_responsible_organization FOREIGN KEY (has_responsible_organization) REFERENCES ct_organizations(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_climate_zone FOREIGN KEY (has_climate_zone) REFERENCES ct_climate_zones(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_webpage_elements FOREIGN KEY (has_webpage_elements) REFERENCES ct_webpage_elements(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_ecosystem_type FOREIGN KEY (has_ecosystem_type) REFERENCES ct_ecosystem_types(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_thematic_centers
ALTER TABLE ct_thematic_centers ADD CONSTRAINT fk_ct_thematic_centers_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);

-- Foreign keys for ct_value_types
ALTER TABLE ct_value_types ADD CONSTRAINT fk_ct_value_types_has_quantity_kind FOREIGN KEY (has_quantity_kind) REFERENCES ct_quantity_kinds(id);
COMMIT;
