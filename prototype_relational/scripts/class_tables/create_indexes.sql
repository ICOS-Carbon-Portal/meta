-- Generated SQL for indexes
-- Source: class_predicates_analysis.json
-- Total tables: 34

-- ======================================================================
-- INDEXES
-- ======================================================================

-- Indexes for ct_object_specs
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_contains_dataset ON ct_object_specs(contains_dataset);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_data_theme ON ct_object_specs(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_encoding ON ct_object_specs(has_encoding);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_format ON ct_object_specs(has_format);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_associated_project ON ct_object_specs(has_associated_project);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_specific_dataset_type ON ct_object_specs(has_specific_dataset_type);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_data_level ON ct_object_specs(has_data_level);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_label ON ct_object_specs(label);

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

-- Indexes for ct_stations
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_responsible_organization ON ct_stations(has_responsible_organization);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_ecosystem_type ON ct_stations(has_ecosystem_type);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_webpage_elements ON ct_stations(has_webpage_elements);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_climate_zone ON ct_stations(has_climate_zone);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_spatial_coverage ON ct_stations(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_name ON ct_stations(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_stations_country ON ct_stations(country);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_atc_id ON ct_stations(has_atc_id);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_etc_id ON ct_stations(has_etc_id);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_otc_id ON ct_stations(has_otc_id);

-- Indexes for ct_dataset_specs
CREATE INDEX IF NOT EXISTS idx_ct_dataset_specs_label ON ct_dataset_specs(label);

-- Indexes for ct_static_objects
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_spatial_coverage ON ct_static_objects(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_object_spec ON ct_static_objects(has_object_spec);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_was_acquired_by ON ct_static_objects(was_acquired_by);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_was_submitted_by ON ct_static_objects(was_submitted_by);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_was_produced_by ON ct_static_objects(was_produced_by);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_name ON ct_static_objects(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_sha256sum ON ct_static_objects(has_sha256sum);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_size_in_bytes ON ct_static_objects(has_size_in_bytes);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_end_time ON ct_static_objects(has_end_time);
CREATE INDEX IF NOT EXISTS idx_ct_static_objects_has_start_time ON ct_static_objects(has_start_time);

-- Indexes for ct_data_submissions
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_was_associated_with ON ct_data_submissions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_ended_at_time ON ct_data_submissions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_started_at_time ON ct_data_submissions(started_at_time);

-- Indexes for ct_data_acquisitions
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_associated_with ON ct_data_acquisitions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_ended_at_time ON ct_data_acquisitions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_started_at_time ON ct_data_acquisitions(started_at_time);

-- Indexes for ct_data_productions
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_performed_by ON ct_data_productions(was_performed_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_hosted_by ON ct_data_productions(was_hosted_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_has_end_time ON ct_data_productions(has_end_time);

-- Indexes for ct_variable_infos
CREATE INDEX IF NOT EXISTS idx_ct_variable_infos_label ON ct_variable_infos(label);
CREATE INDEX IF NOT EXISTS idx_ct_variable_infos_has_max_value ON ct_variable_infos(has_max_value);
CREATE INDEX IF NOT EXISTS idx_ct_variable_infos_has_min_value ON ct_variable_infos(has_min_value);

-- Indexes for ct_instruments
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_instrument_owner ON ct_instruments(has_instrument_owner);
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_vendor ON ct_instruments(has_vendor);
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_model ON ct_instruments(has_model);
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_serial_number ON ct_instruments(has_serial_number);

-- Indexes for ct_memberships
CREATE INDEX IF NOT EXISTS idx_ct_memberships_has_role ON ct_memberships(has_role);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_at_organization ON ct_memberships(at_organization);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_has_start_time ON ct_memberships(has_start_time);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_has_end_time ON ct_memberships(has_end_time);

-- Indexes for ct_persons
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_first_name ON ct_persons(has_first_name);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_last_name ON ct_persons(has_last_name);

-- Indexes for ct_collections
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_spatial_coverage ON ct_collections(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_collections_creator ON ct_collections(creator);
CREATE INDEX IF NOT EXISTS idx_ct_collections_title ON ct_collections(title);
CREATE INDEX IF NOT EXISTS idx_ct_collections_description ON ct_collections(description);

-- Indexes for ct_dataset_columns
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_has_value_format ON ct_dataset_columns(has_value_format);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_has_value_type ON ct_dataset_columns(has_value_type);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_has_column_title ON ct_dataset_columns(has_column_title);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_label ON ct_dataset_columns(label);

-- Indexes for ct_value_types
CREATE INDEX IF NOT EXISTS idx_ct_value_types_has_quantity_kind ON ct_value_types(has_quantity_kind);
CREATE INDEX IF NOT EXISTS idx_ct_value_types_label ON ct_value_types(label);
CREATE INDEX IF NOT EXISTS idx_ct_value_types_has_unit ON ct_value_types(has_unit);

-- Indexes for ct_link_boxes
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_cover_image ON ct_link_boxes(has_cover_image);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_name ON ct_link_boxes(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_order_weight ON ct_link_boxes(has_order_weight);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_label ON ct_link_boxes(label);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_webpage_link ON ct_link_boxes(has_webpage_link);

-- Indexes for ct_fundings
CREATE INDEX IF NOT EXISTS idx_ct_fundings_has_funder ON ct_fundings(has_funder);
CREATE INDEX IF NOT EXISTS idx_ct_fundings_label ON ct_fundings(label);

-- Indexes for ct_dataset_variables
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_has_value_type ON ct_dataset_variables(has_value_type);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_has_variable_title ON ct_dataset_variables(has_variable_title);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_label ON ct_dataset_variables(label);

-- Indexes for ct_plain_collections
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_is_next_version_of ON ct_plain_collections(is_next_version_of);

-- Indexes for ct_funders
CREATE INDEX IF NOT EXISTS idx_ct_funders_has_etc_id ON ct_funders(has_etc_id);
CREATE INDEX IF NOT EXISTS idx_ct_funders_has_name ON ct_funders(has_name);

-- Indexes for ct_climate_zones
CREATE INDEX IF NOT EXISTS idx_ct_climate_zones_label ON ct_climate_zones(label);
CREATE INDEX IF NOT EXISTS idx_ct_climate_zones_see_also ON ct_climate_zones(see_also);

-- Indexes for ct_webpage_elements
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_has_cover_image ON ct_webpage_elements(has_cover_image);
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_label ON ct_webpage_elements(label);
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_comment ON ct_webpage_elements(comment);

-- Indexes for ct_object_formats
CREATE INDEX IF NOT EXISTS idx_ct_object_formats_see_also ON ct_object_formats(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_object_formats_label ON ct_object_formats(label);

-- Indexes for ct_quantity_kinds
CREATE INDEX IF NOT EXISTS idx_ct_quantity_kinds_label ON ct_quantity_kinds(label);

-- Indexes for ct_ecosystem_types
CREATE INDEX IF NOT EXISTS idx_ct_ecosystem_types_label ON ct_ecosystem_types(label);
CREATE INDEX IF NOT EXISTS idx_ct_ecosystem_types_comment ON ct_ecosystem_types(comment);

-- Indexes for ct_value_formats
CREATE INDEX IF NOT EXISTS idx_ct_value_formats_label ON ct_value_formats(label);

-- Indexes for ct_projects
CREATE INDEX IF NOT EXISTS idx_ct_projects_comment ON ct_projects(comment);
CREATE INDEX IF NOT EXISTS idx_ct_projects_label ON ct_projects(label);

-- Indexes for ct_roles
CREATE INDEX IF NOT EXISTS idx_ct_roles_label ON ct_roles(label);

-- Indexes for ct_data_themes
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_has_icon ON ct_data_themes(has_icon);
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_has_marker_icon ON ct_data_themes(has_marker_icon);
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_label ON ct_data_themes(label);

-- Indexes for ct_object_encodings
CREATE INDEX IF NOT EXISTS idx_ct_object_encodings_label ON ct_object_encodings(label);

-- Indexes for ct_thematic_centers
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_data_theme ON ct_thematic_centers(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_name ON ct_thematic_centers(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_label ON ct_thematic_centers(label);

-- Indexes for ct_central_facilities
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_has_name ON ct_central_facilities(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_label ON ct_central_facilities(label);

-- Indexes for ct_specific_dataset_types
CREATE INDEX IF NOT EXISTS idx_ct_specific_dataset_types_label ON ct_specific_dataset_types(label);
