-- Generated SQL for class-based tables
-- Source: class_predicates_analysis.json
-- Total tables: 46

-- ======================================================================
-- CREATE TABLES
-- ======================================================================

-- Table: ct_data_submissions
-- Class: cpmeta:DataSubmission (2,346,277 instances)

CREATE TABLE IF NOT EXISTS ct_data_submissions (
    id TEXT PRIMARY KEY,
    ended_at_time TIMESTAMP WITH TIME ZONE,
    started_at_time TIMESTAMP WITH TIME ZONE,
    was_associated_with TEXT
);

-- Table: ct_data_objects
-- Class: cpmeta:DataObject (2,345,839 instances)

CREATE TABLE IF NOT EXISTS ct_data_objects (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    has_object_spec TEXT,
    has_sha256sum TEXT,
    has_size_in_bytes BIGINT,
    was_submitted_by TEXT,
    was_acquired_by TEXT,
    has_number_of_rows INTEGER,
    was_produced_by TEXT,
    is_next_version_of TEXT,
    has_actual_column_names TEXT,
    had_primary_source TEXT,
    has_spatial_coverage TEXT,
    has_actual_variable TEXT,
    has_doi TEXT,
    has_keywords TEXT,
    contact_20_point TEXT,
    contributor TEXT,
    measurement_20_method TEXT,
    measurement_20_scale TEXT,
    measurement_20_unit TEXT,
    observation_20_category TEXT,
    parameter TEXT,
    sampling_20_type TEXT,
    time_20_interval TEXT,
    has_end_time TIMESTAMP WITH TIME ZONE,
    has_start_time TIMESTAMP WITH TIME ZONE,
    has_temporal_resolution TEXT,
    description TEXT,
    title TEXT,
    license TEXT,
    see_also TEXT
);

-- Table: ct_data_acquisitions
-- Class: cpmeta:DataAcquisition (2,343,194 instances)

CREATE TABLE IF NOT EXISTS ct_data_acquisitions (
    id TEXT PRIMARY KEY,
    was_performed_with TEXT,
    ended_at_time TIMESTAMP WITH TIME ZONE,
    started_at_time TIMESTAMP WITH TIME ZONE,
    was_associated_with TEXT,
    has_sampling_height DOUBLE PRECISION,
    has_sampling_point TEXT,
    was_performed_at TEXT
);

-- Table: ct_data_productions
-- Class: cpmeta:DataProduction (1,248,515 instances)

CREATE TABLE IF NOT EXISTS ct_data_productions (
    id TEXT PRIMARY KEY,
    has_end_time TIMESTAMP WITH TIME ZONE,
    was_performed_by TEXT,
    was_hosted_by TEXT,
    was_participated_in_by TEXT,
    comment TEXT,
    see_also TEXT
);

-- Table: ct_variable_infos
-- Class: cpmeta:VariableInfo (4,957 instances)

CREATE TABLE IF NOT EXISTS ct_variable_infos (
    id TEXT PRIMARY KEY,
    label TEXT,
    has_max_value DOUBLE PRECISION,
    has_min_value DOUBLE PRECISION
);

-- Table: ct_instruments
-- Class: cpmeta:Instrument (4,825 instances)

CREATE TABLE IF NOT EXISTS ct_instruments (
    id TEXT PRIMARY KEY,
    has_model TEXT,
    has_serial_number TEXT,
    has_vendor TEXT,
    www_w3_org_ns_ssn_has_deployment TEXT,
    has_etc_id TEXT,
    comment TEXT,
    has_name TEXT,
    has_atc_id TEXT,
    has_instrument_owner TEXT,
    has_instrument_component TEXT,
    has_otc_id TEXT
);

-- Table: ct_spatial_coverages
-- Class: cpmeta:SpatialCoverage (3,962 instances)

CREATE TABLE IF NOT EXISTS ct_spatial_coverages (
    id TEXT PRIMARY KEY,
    as_geo_json TEXT,
    label TEXT
);

-- Table: ct_memberships
-- Class: cpmeta:Membership (1,881 instances)

CREATE TABLE IF NOT EXISTS ct_memberships (
    id TEXT PRIMARY KEY,
    label TEXT,
    has_role TEXT,
    at_organization TEXT,
    has_start_time TIMESTAMP WITH TIME ZONE,
    has_attribution_weight SMALLINT,
    has_end_time TIMESTAMP WITH TIME ZONE,
    has_extra_role_info TEXT
);

-- Table: ct_persons
-- Class: cpmeta:Person (1,144 instances)

CREATE TABLE IF NOT EXISTS ct_persons (
    id TEXT PRIMARY KEY,
    has_membership TEXT,
    has_first_name TEXT,
    has_last_name TEXT,
    has_email TEXT,
    has_etc_id TEXT,
    has_orcid_id TEXT,
    has_atc_id TEXT,
    has_otc_id TEXT,
    label TEXT,
    comment TEXT
);

-- Table: ct_collections
-- Class: cpmeta:Collection (786 instances)

CREATE TABLE IF NOT EXISTS ct_collections (
    id TEXT PRIMARY KEY,
    has_part TEXT,
    creator TEXT,
    title TEXT,
    description TEXT,
    is_next_version_of TEXT,
    has_doi TEXT,
    has_spatial_coverage TEXT,
    see_also TEXT
);

-- Table: ct_document_objects
-- Class: cpmeta:DocumentObject (438 instances)

CREATE TABLE IF NOT EXISTS ct_document_objects (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    has_sha256sum TEXT,
    has_size_in_bytes BIGINT,
    was_submitted_by TEXT,
    creator TEXT,
    title TEXT,
    description TEXT,
    is_next_version_of TEXT,
    has_doi TEXT
);

-- Table: ct_dataset_columns
-- Class: cpmeta:DatasetColumn (420 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_columns (
    id TEXT PRIMARY KEY,
    has_column_title TEXT,
    has_value_format TEXT,
    has_value_type TEXT,
    label TEXT,
    is_optional_column BOOLEAN,
    is_regex_column BOOLEAN,
    comment TEXT,
    is_quality_flag_for TEXT,
    see_also TEXT
);

-- Table: ct_value_types
-- Class: cpmeta:ValueType (307 instances)

CREATE TABLE IF NOT EXISTS ct_value_types (
    id TEXT PRIMARY KEY,
    label TEXT,
    has_quantity_kind TEXT,
    has_unit TEXT,
    comment TEXT,
    www_w3_org_2004_02_skos_core_exact_match TEXT,
    see_also TEXT
);

-- Table: ct_lat_lon_boxes
-- Class: cpmeta:LatLonBox (299 instances)

CREATE TABLE IF NOT EXISTS ct_lat_lon_boxes (
    id TEXT PRIMARY KEY,
    has_eastern_bound DOUBLE PRECISION,
    has_northern_bound DOUBLE PRECISION,
    has_southern_bound DOUBLE PRECISION,
    has_western_bound DOUBLE PRECISION,
    as_geo_json TEXT,
    label TEXT
);

-- Table: ct_es
-- Class: cpmeta:ES (278 instances)

CREATE TABLE IF NOT EXISTS ct_es (
    id TEXT PRIMARY KEY,
    www_w3_org_ns_dcat_theme TEXT,
    country_code TEXT,
    has_etc_id TEXT,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    has_name TEXT,
    has_station_id TEXT,
    label TEXT,
    has_ecosystem_type TEXT,
    has_time_zone_offset SMALLINT,
    has_elevation DOUBLE PRECISION,
    has_mean_annual_precip DOUBLE PRECISION,
    has_mean_annual_temp DOUBLE PRECISION,
    comment TEXT,
    has_funding TEXT,
    has_station_class TEXT,
    www_w3_org_ns_dcat_contact_point TEXT,
    identifier TEXT,
    is_part_of TEXT,
    spatial TEXT,
    subject TEXT,
    title TEXT,
    has_climate_zone TEXT,
    has_depiction TEXT,
    description TEXT,
    has_mean_annual_radiation DOUBLE PRECISION,
    has_associated_publication TEXT,
    has_documentation_object TEXT,
    has_labeling_date DATE,
    has_spatial_coverage TEXT,
    has_webpage_elements TEXT,
    is_discontinued BOOLEAN,
    has_documentation_uri TEXT
);

-- Table: ct_organizations
-- Class: cpmeta:Organization (265 instances)

CREATE TABLE IF NOT EXISTS ct_organizations (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    label TEXT,
    has_atc_id TEXT,
    has_otc_id TEXT,
    has_etc_id TEXT,
    see_also TEXT,
    has_email TEXT
);

-- Table: ct_stations
-- Class: cpmeta:Station (246 instances)

CREATE TABLE IF NOT EXISTS ct_stations (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    country TEXT,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    country_code TEXT,
    has_station_id TEXT,
    has_elevation DOUBLE PRECISION,
    has_responsible_organization TEXT,
    has_time_zone_offset SMALLINT,
    label TEXT,
    comment TEXT,
    has_climate_zone TEXT,
    has_documentation_uri TEXT,
    has_spatial_coverage TEXT
);

-- Table: ct_positions
-- Class: cpmeta:Position (205 instances)

CREATE TABLE IF NOT EXISTS ct_positions (
    id TEXT PRIMARY KEY,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    label TEXT,
    as_geo_json TEXT
);

-- Table: ct_link_boxes
-- Class: cpmeta:LinkBox (184 instances)

CREATE TABLE IF NOT EXISTS ct_link_boxes (
    id TEXT PRIMARY KEY,
    has_cover_image TEXT,
    has_name TEXT,
    has_order_weight SMALLINT,
    label TEXT,
    has_webpage_link TEXT,
    comment TEXT
);

-- Table: ct_fundings
-- Class: cpmeta:Funding (109 instances)

CREATE TABLE IF NOT EXISTS ct_fundings (
    id TEXT PRIMARY KEY,
    has_funder TEXT,
    label TEXT,
    has_end_date DATE,
    has_start_date DATE,
    award_title TEXT,
    award_number TEXT,
    award_uri TEXT,
    comment TEXT
);

-- Table: ct_sites
-- Class: cpmeta:Site (99 instances)

CREATE TABLE IF NOT EXISTS ct_sites (
    id TEXT PRIMARY KEY,
    has_sampling_point TEXT,
    has_ecosystem_type TEXT,
    has_spatial_coverage TEXT,
    label TEXT
);

-- Table: ct_simple_object_specs
-- Class: cpmeta:SimpleObjectSpec (97 instances)

CREATE TABLE IF NOT EXISTS ct_simple_object_specs (
    id TEXT PRIMARY KEY,
    has_associated_project TEXT,
    has_data_level SMALLINT,
    has_data_theme TEXT,
    has_encoding TEXT,
    has_format TEXT,
    has_specific_dataset_type TEXT,
    label TEXT,
    contains_dataset TEXT,
    has_keywords TEXT,
    comment TEXT,
    has_documentation_object TEXT,
    implies_default_licence TEXT,
    see_also TEXT
);

-- Table: ct_data_object_specs
-- Class: cpmeta:DataObjectSpec (81 instances)

CREATE TABLE IF NOT EXISTS ct_data_object_specs (
    id TEXT PRIMARY KEY,
    has_associated_project TEXT,
    has_data_level SMALLINT,
    has_data_theme TEXT,
    has_encoding TEXT,
    has_format TEXT,
    has_specific_dataset_type TEXT,
    label TEXT,
    comment TEXT,
    has_keywords TEXT,
    contains_dataset TEXT,
    see_also TEXT,
    has_documentation_object TEXT
);

-- Table: ct_dataset_variables
-- Class: cpmeta:DatasetVariable (76 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_variables (
    id TEXT PRIMARY KEY,
    has_value_type TEXT,
    has_variable_title TEXT,
    label TEXT,
    is_optional_variable BOOLEAN
);

-- Table: ct_as
-- Class: cpmeta:AS (70 instances)

CREATE TABLE IF NOT EXISTS ct_as (
    id TEXT PRIMARY KEY,
    www_w3_org_ns_dcat_theme TEXT,
    country_code TEXT,
    has_atc_id TEXT,
    has_elevation DOUBLE PRECISION,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    has_name TEXT,
    has_responsible_organization TEXT,
    has_station_id TEXT,
    has_time_zone_offset SMALLINT,
    label TEXT,
    has_wigos_id TEXT,
    has_station_class TEXT,
    has_documentation_object TEXT,
    has_depiction TEXT,
    has_labeling_date DATE,
    www_w3_org_ns_dcat_contact_point TEXT,
    identifier TEXT,
    is_part_of TEXT,
    spatial TEXT,
    subject TEXT,
    title TEXT,
    has_webpage_elements TEXT
);

-- Table: ct_tabular_dataset_specs
-- Class: cpmeta:TabularDatasetSpec (68 instances)

CREATE TABLE IF NOT EXISTS ct_tabular_dataset_specs (
    id TEXT PRIMARY KEY,
    has_column TEXT,
    label TEXT,
    has_temporal_resolution TEXT,
    comment TEXT
);

-- Table: ct_plain_collections
-- Class: cpmeta:PlainCollection (50 instances)

CREATE TABLE IF NOT EXISTS ct_plain_collections (
    id TEXT PRIMARY KEY,
    has_part TEXT,
    is_next_version_of TEXT
);

-- Table: ct_funders
-- Class: cpmeta:Funder (45 instances)

CREATE TABLE IF NOT EXISTS ct_funders (
    id TEXT PRIMARY KEY,
    has_etc_id TEXT,
    has_name TEXT
);

-- Table: ct_ecosystem_types
-- Class: cpmeta:EcosystemType (41 instances)

CREATE TABLE IF NOT EXISTS ct_ecosystem_types (
    id TEXT PRIMARY KEY,
    label TEXT,
    comment TEXT,
    see_also TEXT
);

-- Table: ct_webpage_elements
-- Class: cpmeta:WebpageElements (37 instances)

CREATE TABLE IF NOT EXISTS ct_webpage_elements (
    id TEXT PRIMARY KEY,
    has_linkbox TEXT,
    has_cover_image TEXT,
    label TEXT,
    comment TEXT
);

-- Table: ct_climate_zones
-- Class: cpmeta:ClimateZone (35 instances)

CREATE TABLE IF NOT EXISTS ct_climate_zones (
    id TEXT PRIMARY KEY,
    label TEXT,
    see_also TEXT
);

-- Table: ct_quantity_kinds
-- Class: cpmeta:QuantityKind (31 instances)

CREATE TABLE IF NOT EXISTS ct_quantity_kinds (
    id TEXT PRIMARY KEY,
    label TEXT,
    comment TEXT
);

-- Table: ct_object_formats
-- Class: cpmeta:ObjectFormat (28 instances)

CREATE TABLE IF NOT EXISTS ct_object_formats (
    id TEXT PRIMARY KEY,
    label TEXT,
    has_good_flag_value TEXT,
    comment TEXT,
    see_also TEXT
);

-- Table: ct_os
-- Class: cpmeta:OS (25 instances)

CREATE TABLE IF NOT EXISTS ct_os (
    id TEXT PRIMARY KEY,
    www_w3_org_ns_dcat_theme TEXT,
    country_code TEXT,
    has_name TEXT,
    has_otc_id TEXT,
    has_responsible_organization TEXT,
    has_station_class TEXT,
    has_station_id TEXT,
    label TEXT,
    www_w3_org_ns_dcat_contact_point TEXT,
    spatial TEXT,
    identifier TEXT,
    is_part_of TEXT,
    subject TEXT,
    title TEXT,
    comment TEXT,
    description TEXT,
    has_spatial_coverage TEXT,
    see_also TEXT,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    has_labeling_date DATE,
    has_webpage_elements TEXT,
    has_depiction TEXT,
    is_discontinued BOOLEAN
);

-- Table: ct_projects
-- Class: cpmeta:Project (18 instances)

CREATE TABLE IF NOT EXISTS ct_projects (
    id TEXT PRIMARY KEY,
    label TEXT,
    comment TEXT,
    see_also TEXT,
    has_keywords TEXT,
    has_hide_from_search_policy BOOLEAN,
    has_skip_pid_minting_policy BOOLEAN,
    has_skip_storage_policy BOOLEAN
);

-- Table: ct_value_formats
-- Class: cpmeta:ValueFormat (13 instances)

CREATE TABLE IF NOT EXISTS ct_value_formats (
    id TEXT PRIMARY KEY,
    label TEXT,
    comment TEXT
);

-- Table: ct_dataset_specs
-- Class: cpmeta:DatasetSpec (12 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_specs (
    id TEXT PRIMARY KEY,
    has_variable TEXT,
    label TEXT,
    has_temporal_resolution TEXT
);

-- Table: ct_data_themes
-- Class: cpmeta:DataTheme (9 instances)

CREATE TABLE IF NOT EXISTS ct_data_themes (
    id TEXT PRIMARY KEY,
    has_icon TEXT,
    label TEXT,
    has_marker_icon TEXT
);

-- Table: ct_roles
-- Class: cpmeta:Role (5 instances)

CREATE TABLE IF NOT EXISTS ct_roles (
    id TEXT PRIMARY KEY,
    label TEXT,
    comment TEXT
);

-- Table: ct_atmo_stations
-- Class: cpmeta:AtmoStation (4 instances)

CREATE TABLE IF NOT EXISTS ct_atmo_stations (
    id TEXT PRIMARY KEY,
    country_code TEXT,
    has_elevation DOUBLE PRECISION,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    has_name TEXT,
    has_station_id TEXT
);

-- Table: ct_thematic_centers
-- Class: cpmeta:ThematicCenter (4 instances)

CREATE TABLE IF NOT EXISTS ct_thematic_centers (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    label TEXT,
    has_data_theme TEXT
);

-- Table: ct_ingos_stations
-- Class: cpmeta:IngosStation (3 instances)

CREATE TABLE IF NOT EXISTS ct_ingos_stations (
    id TEXT PRIMARY KEY,
    country_code TEXT,
    has_elevation DOUBLE PRECISION,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    has_name TEXT,
    has_station_id TEXT
);

-- Table: ct_object_encodings
-- Class: cpmeta:ObjectEncoding (3 instances)

CREATE TABLE IF NOT EXISTS ct_object_encodings (
    id TEXT PRIMARY KEY,
    label TEXT
);

-- Table: ct_central_facilities
-- Class: cpmeta:CentralFacility (2 instances)

CREATE TABLE IF NOT EXISTS ct_central_facilities (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    label TEXT,
    comment TEXT
);

-- Table: ct_sail_drones
-- Class: cpmeta:SailDrone (2 instances)

CREATE TABLE IF NOT EXISTS ct_sail_drones (
    id TEXT PRIMARY KEY,
    www_w3_org_ns_dcat_theme TEXT,
    country_code TEXT,
    has_elevation DOUBLE PRECISION,
    has_name TEXT,
    has_station_id TEXT
);

-- Table: ct_specific_dataset_types
-- Class: cpmeta:SpecificDatasetType (2 instances)

CREATE TABLE IF NOT EXISTS ct_specific_dataset_types (
    id TEXT PRIMARY KEY,
    label TEXT
);

-- ======================================================================
-- FOREIGN KEY CONSTRAINTS
-- ======================================================================

-- Foreign keys for ct_as
ALTER TABLE ct_as ADD CONSTRAINT fk_ct_as_has_responsible_organization FOREIGN KEY (has_responsible_organization) REFERENCES ct_organizations(id);
ALTER TABLE ct_as ADD CONSTRAINT fk_ct_as_has_documentation_object FOREIGN KEY (has_documentation_object) REFERENCES ct_document_objects(id);
ALTER TABLE ct_as ADD CONSTRAINT fk_ct_as_has_webpage_elements FOREIGN KEY (has_webpage_elements) REFERENCES ct_webpage_elements(id);

-- Foreign keys for ct_collections
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_data_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_data_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_document_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_see_also FOREIGN KEY (see_also) REFERENCES ct_document_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_creator FOREIGN KEY (creator) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_creator FOREIGN KEY (creator) REFERENCES ct_organizations(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_positions(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_lat_lon_boxes(id);

-- Foreign keys for ct_data_acquisitions
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_performed_with FOREIGN KEY (was_performed_with) REFERENCES ct_instruments(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_as(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_es(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_os(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_stations(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_performed_at FOREIGN KEY (was_performed_at) REFERENCES ct_sites(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_has_sampling_point FOREIGN KEY (has_sampling_point) REFERENCES ct_positions(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_sail_drones(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_ingos_stations(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_atmo_stations(id);

-- Foreign keys for ct_data_object_specs
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_has_encoding FOREIGN KEY (has_encoding) REFERENCES ct_object_encodings(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_has_format FOREIGN KEY (has_format) REFERENCES ct_object_formats(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_has_associated_project FOREIGN KEY (has_associated_project) REFERENCES ct_projects(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_has_specific_dataset_type FOREIGN KEY (has_specific_dataset_type) REFERENCES ct_specific_dataset_types(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_contains_dataset FOREIGN KEY (contains_dataset) REFERENCES ct_dataset_specs(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_has_documentation_object FOREIGN KEY (has_documentation_object) REFERENCES ct_document_objects(id);
ALTER TABLE ct_data_object_specs ADD CONSTRAINT fk_ct_data_object_specs_contains_dataset FOREIGN KEY (contains_dataset) REFERENCES ct_tabular_dataset_specs(id);

-- Foreign keys for ct_data_objects
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_acquired_by FOREIGN KEY (was_acquired_by) REFERENCES ct_data_acquisitions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_produced_by FOREIGN KEY (was_produced_by) REFERENCES ct_data_productions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_object_spec FOREIGN KEY (has_object_spec) REFERENCES ct_simple_object_specs(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_object_spec FOREIGN KEY (has_object_spec) REFERENCES ct_data_object_specs(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_actual_variable FOREIGN KEY (has_actual_variable) REFERENCES ct_variable_infos(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_lat_lon_boxes(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_positions(id);

-- Foreign keys for ct_data_productions
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_hosted_by FOREIGN KEY (was_hosted_by) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_participated_in_by FOREIGN KEY (was_participated_in_by) REFERENCES ct_organizations(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_hosted_by FOREIGN KEY (was_hosted_by) REFERENCES ct_organizations(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_organizations(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_participated_in_by FOREIGN KEY (was_participated_in_by) REFERENCES ct_persons(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_persons(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_hosted_by FOREIGN KEY (was_hosted_by) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_participated_in_by FOREIGN KEY (was_participated_in_by) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_see_also FOREIGN KEY (see_also) REFERENCES ct_document_objects(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_hosted_by FOREIGN KEY (was_hosted_by) REFERENCES ct_es(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_es(id);

-- Foreign keys for ct_data_submissions
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_es(id);
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_organizations(id);

-- Foreign keys for ct_dataset_columns
ALTER TABLE ct_dataset_columns ADD CONSTRAINT fk_ct_dataset_columns_has_value_format FOREIGN KEY (has_value_format) REFERENCES ct_value_formats(id);
ALTER TABLE ct_dataset_columns ADD CONSTRAINT fk_ct_dataset_columns_has_value_type FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id);

-- Foreign keys for ct_dataset_specs
ALTER TABLE ct_dataset_specs ADD CONSTRAINT fk_ct_dataset_specs_has_variable FOREIGN KEY (has_variable) REFERENCES ct_dataset_variables(id);

-- Foreign keys for ct_dataset_variables
ALTER TABLE ct_dataset_variables ADD CONSTRAINT fk_ct_dataset_variables_has_value_type FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id);

-- Foreign keys for ct_document_objects
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_creator FOREIGN KEY (creator) REFERENCES ct_persons(id);
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_creator FOREIGN KEY (creator) REFERENCES ct_organizations(id);

-- Foreign keys for ct_es
ALTER TABLE ct_es ADD CONSTRAINT fk_ct_es_has_ecosystem_type FOREIGN KEY (has_ecosystem_type) REFERENCES ct_ecosystem_types(id);
ALTER TABLE ct_es ADD CONSTRAINT fk_ct_es_has_funding FOREIGN KEY (has_funding) REFERENCES ct_fundings(id);
ALTER TABLE ct_es ADD CONSTRAINT fk_ct_es_has_climate_zone FOREIGN KEY (has_climate_zone) REFERENCES ct_climate_zones(id);
ALTER TABLE ct_es ADD CONSTRAINT fk_ct_es_has_documentation_object FOREIGN KEY (has_documentation_object) REFERENCES ct_document_objects(id);
ALTER TABLE ct_es ADD CONSTRAINT fk_ct_es_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);
ALTER TABLE ct_es ADD CONSTRAINT fk_ct_es_has_webpage_elements FOREIGN KEY (has_webpage_elements) REFERENCES ct_webpage_elements(id);

-- Foreign keys for ct_fundings
ALTER TABLE ct_fundings ADD CONSTRAINT fk_ct_fundings_has_funder FOREIGN KEY (has_funder) REFERENCES ct_funders(id);

-- Foreign keys for ct_instruments
ALTER TABLE ct_instruments ADD CONSTRAINT fk_ct_instruments_has_vendor FOREIGN KEY (has_vendor) REFERENCES ct_organizations(id);
ALTER TABLE ct_instruments ADD CONSTRAINT fk_ct_instruments_has_instrument_owner FOREIGN KEY (has_instrument_owner) REFERENCES ct_organizations(id);

-- Foreign keys for ct_link_boxes
ALTER TABLE ct_link_boxes ADD CONSTRAINT fk_ct_link_boxes_has_webpage_link FOREIGN KEY (has_webpage_link) REFERENCES ct_document_objects(id);

-- Foreign keys for ct_memberships
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_has_role FOREIGN KEY (has_role) REFERENCES ct_roles(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_es(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_as(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_organizations(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_os(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_memberships ADD CONSTRAINT fk_ct_memberships_at_organization FOREIGN KEY (at_organization) REFERENCES ct_thematic_centers(id);

-- Foreign keys for ct_object_formats
ALTER TABLE ct_object_formats ADD CONSTRAINT fk_ct_object_formats_see_also FOREIGN KEY (see_also) REFERENCES ct_value_formats(id);

-- Foreign keys for ct_os
ALTER TABLE ct_os ADD CONSTRAINT fk_ct_os_has_responsible_organization FOREIGN KEY (has_responsible_organization) REFERENCES ct_organizations(id);
ALTER TABLE ct_os ADD CONSTRAINT fk_ct_os_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);
ALTER TABLE ct_os ADD CONSTRAINT fk_ct_os_has_webpage_elements FOREIGN KEY (has_webpage_elements) REFERENCES ct_webpage_elements(id);

-- Foreign keys for ct_persons
ALTER TABLE ct_persons ADD CONSTRAINT fk_ct_persons_has_membership FOREIGN KEY (has_membership) REFERENCES ct_memberships(id);

-- Foreign keys for ct_plain_collections
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_data_objects(id);
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_data_objects(id);

-- Foreign keys for ct_simple_object_specs
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_has_encoding FOREIGN KEY (has_encoding) REFERENCES ct_object_encodings(id);
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_has_format FOREIGN KEY (has_format) REFERENCES ct_object_formats(id);
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_has_associated_project FOREIGN KEY (has_associated_project) REFERENCES ct_projects(id);
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_has_specific_dataset_type FOREIGN KEY (has_specific_dataset_type) REFERENCES ct_specific_dataset_types(id);
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_contains_dataset FOREIGN KEY (contains_dataset) REFERENCES ct_tabular_dataset_specs(id);
ALTER TABLE ct_simple_object_specs ADD CONSTRAINT fk_ct_simple_object_specs_has_documentation_object FOREIGN KEY (has_documentation_object) REFERENCES ct_document_objects(id);

-- Foreign keys for ct_sites
ALTER TABLE ct_sites ADD CONSTRAINT fk_ct_sites_has_sampling_point FOREIGN KEY (has_sampling_point) REFERENCES ct_positions(id);
ALTER TABLE ct_sites ADD CONSTRAINT fk_ct_sites_has_ecosystem_type FOREIGN KEY (has_ecosystem_type) REFERENCES ct_ecosystem_types(id);
ALTER TABLE ct_sites ADD CONSTRAINT fk_ct_sites_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_stations
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_responsible_organization FOREIGN KEY (has_responsible_organization) REFERENCES ct_organizations(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_climate_zone FOREIGN KEY (has_climate_zone) REFERENCES ct_climate_zones(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_lat_lon_boxes(id);

-- Foreign keys for ct_tabular_dataset_specs
ALTER TABLE ct_tabular_dataset_specs ADD CONSTRAINT fk_ct_tabular_dataset_specs_has_column FOREIGN KEY (has_column) REFERENCES ct_dataset_columns(id);

-- Foreign keys for ct_thematic_centers
ALTER TABLE ct_thematic_centers ADD CONSTRAINT fk_ct_thematic_centers_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);

-- Foreign keys for ct_value_types
ALTER TABLE ct_value_types ADD CONSTRAINT fk_ct_value_types_has_quantity_kind FOREIGN KEY (has_quantity_kind) REFERENCES ct_quantity_kinds(id);

-- Foreign keys for ct_webpage_elements
ALTER TABLE ct_webpage_elements ADD CONSTRAINT fk_ct_webpage_elements_has_linkbox FOREIGN KEY (has_linkbox) REFERENCES ct_link_boxes(id);

-- ======================================================================
-- INDEXES
-- ======================================================================

-- Indexes for ct_data_submissions
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_was_associated_with ON ct_data_submissions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_ended_at_time ON ct_data_submissions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_started_at_time ON ct_data_submissions(started_at_time);

-- Indexes for ct_data_objects
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_produced_by ON ct_data_objects(was_produced_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_actual_variable ON ct_data_objects(has_actual_variable);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_object_spec ON ct_data_objects(has_object_spec);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by ON ct_data_objects(was_acquired_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_submitted_by ON ct_data_objects(was_submitted_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_spatial_coverage ON ct_data_objects(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_name ON ct_data_objects(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_sha256sum ON ct_data_objects(has_sha256sum);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_size_in_bytes ON ct_data_objects(has_size_in_bytes);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_end_time ON ct_data_objects(has_end_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_start_time ON ct_data_objects(has_start_time);

-- Indexes for ct_data_acquisitions
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_performed_at ON ct_data_acquisitions(was_performed_at);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_has_sampling_point ON ct_data_acquisitions(has_sampling_point);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_associated_with ON ct_data_acquisitions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_performed_with ON ct_data_acquisitions(was_performed_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_ended_at_time ON ct_data_acquisitions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_started_at_time ON ct_data_acquisitions(started_at_time);

-- Indexes for ct_data_productions
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_see_also ON ct_data_productions(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_participated_in_by ON ct_data_productions(was_participated_in_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_hosted_by ON ct_data_productions(was_hosted_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_performed_by ON ct_data_productions(was_performed_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_has_end_time ON ct_data_productions(has_end_time);

-- Indexes for ct_variable_infos
CREATE INDEX IF NOT EXISTS idx_ct_variable_infos_label ON ct_variable_infos(label);
CREATE INDEX IF NOT EXISTS idx_ct_variable_infos_has_max_value ON ct_variable_infos(has_max_value);
CREATE INDEX IF NOT EXISTS idx_ct_variable_infos_has_min_value ON ct_variable_infos(has_min_value);

-- Indexes for ct_instruments
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_vendor ON ct_instruments(has_vendor);
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_instrument_owner ON ct_instruments(has_instrument_owner);
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_model ON ct_instruments(has_model);
CREATE INDEX IF NOT EXISTS idx_ct_instruments_has_serial_number ON ct_instruments(has_serial_number);

-- Indexes for ct_spatial_coverages
CREATE INDEX IF NOT EXISTS idx_ct_spatial_coverages_as_geo_json ON ct_spatial_coverages(as_geo_json);

-- Indexes for ct_memberships
CREATE INDEX IF NOT EXISTS idx_ct_memberships_at_organization ON ct_memberships(at_organization);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_has_role ON ct_memberships(has_role);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_label ON ct_memberships(label);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_has_start_time ON ct_memberships(has_start_time);
CREATE INDEX IF NOT EXISTS idx_ct_memberships_has_end_time ON ct_memberships(has_end_time);

-- Indexes for ct_persons
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_membership ON ct_persons(has_membership);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_first_name ON ct_persons(has_first_name);
CREATE INDEX IF NOT EXISTS idx_ct_persons_has_last_name ON ct_persons(has_last_name);

-- Indexes for ct_collections
CREATE INDEX IF NOT EXISTS idx_ct_collections_see_also ON ct_collections(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_collections_creator ON ct_collections(creator);
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_part ON ct_collections(has_part);
CREATE INDEX IF NOT EXISTS idx_ct_collections_is_next_version_of ON ct_collections(is_next_version_of);
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_spatial_coverage ON ct_collections(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_collections_title ON ct_collections(title);
CREATE INDEX IF NOT EXISTS idx_ct_collections_description ON ct_collections(description);

-- Indexes for ct_document_objects
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_was_submitted_by ON ct_document_objects(was_submitted_by);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_creator ON ct_document_objects(creator);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_has_name ON ct_document_objects(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_has_sha256sum ON ct_document_objects(has_sha256sum);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_has_size_in_bytes ON ct_document_objects(has_size_in_bytes);

-- Indexes for ct_dataset_columns
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_has_value_format ON ct_dataset_columns(has_value_format);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_has_value_type ON ct_dataset_columns(has_value_type);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_has_column_title ON ct_dataset_columns(has_column_title);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_columns_label ON ct_dataset_columns(label);

-- Indexes for ct_value_types
CREATE INDEX IF NOT EXISTS idx_ct_value_types_has_quantity_kind ON ct_value_types(has_quantity_kind);
CREATE INDEX IF NOT EXISTS idx_ct_value_types_label ON ct_value_types(label);

-- Indexes for ct_lat_lon_boxes
CREATE INDEX IF NOT EXISTS idx_ct_lat_lon_boxes_has_eastern_bound ON ct_lat_lon_boxes(has_eastern_bound);
CREATE INDEX IF NOT EXISTS idx_ct_lat_lon_boxes_has_northern_bound ON ct_lat_lon_boxes(has_northern_bound);
CREATE INDEX IF NOT EXISTS idx_ct_lat_lon_boxes_has_southern_bound ON ct_lat_lon_boxes(has_southern_bound);
CREATE INDEX IF NOT EXISTS idx_ct_lat_lon_boxes_has_western_bound ON ct_lat_lon_boxes(has_western_bound);
CREATE INDEX IF NOT EXISTS idx_ct_lat_lon_boxes_as_geo_json ON ct_lat_lon_boxes(as_geo_json);

-- Indexes for ct_es
CREATE INDEX IF NOT EXISTS idx_ct_es_has_documentation_object ON ct_es(has_documentation_object);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_funding ON ct_es(has_funding);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_climate_zone ON ct_es(has_climate_zone);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_ecosystem_type ON ct_es(has_ecosystem_type);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_webpage_elements ON ct_es(has_webpage_elements);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_spatial_coverage ON ct_es(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_es_www_w3_org_ns_dcat_theme ON ct_es(www_w3_org_ns_dcat_theme);
CREATE INDEX IF NOT EXISTS idx_ct_es_country_code ON ct_es(country_code);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_etc_id ON ct_es(has_etc_id);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_latitude ON ct_es(has_latitude);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_longitude ON ct_es(has_longitude);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_name ON ct_es(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_station_id ON ct_es(has_station_id);
CREATE INDEX IF NOT EXISTS idx_ct_es_label ON ct_es(label);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_time_zone_offset ON ct_es(has_time_zone_offset);
CREATE INDEX IF NOT EXISTS idx_ct_es_has_elevation ON ct_es(has_elevation);

-- Indexes for ct_organizations
CREATE INDEX IF NOT EXISTS idx_ct_organizations_has_name ON ct_organizations(has_name);

-- Indexes for ct_stations
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_climate_zone ON ct_stations(has_climate_zone);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_responsible_organization ON ct_stations(has_responsible_organization);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_spatial_coverage ON ct_stations(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_name ON ct_stations(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_stations_country ON ct_stations(country);

-- Indexes for ct_positions
CREATE INDEX IF NOT EXISTS idx_ct_positions_has_latitude ON ct_positions(has_latitude);
CREATE INDEX IF NOT EXISTS idx_ct_positions_has_longitude ON ct_positions(has_longitude);
CREATE INDEX IF NOT EXISTS idx_ct_positions_label ON ct_positions(label);

-- Indexes for ct_link_boxes
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_webpage_link ON ct_link_boxes(has_webpage_link);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_cover_image ON ct_link_boxes(has_cover_image);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_name ON ct_link_boxes(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_has_order_weight ON ct_link_boxes(has_order_weight);
CREATE INDEX IF NOT EXISTS idx_ct_link_boxes_label ON ct_link_boxes(label);

-- Indexes for ct_fundings
CREATE INDEX IF NOT EXISTS idx_ct_fundings_has_funder ON ct_fundings(has_funder);
CREATE INDEX IF NOT EXISTS idx_ct_fundings_label ON ct_fundings(label);

-- Indexes for ct_sites
CREATE INDEX IF NOT EXISTS idx_ct_sites_has_ecosystem_type ON ct_sites(has_ecosystem_type);
CREATE INDEX IF NOT EXISTS idx_ct_sites_has_sampling_point ON ct_sites(has_sampling_point);
CREATE INDEX IF NOT EXISTS idx_ct_sites_has_spatial_coverage ON ct_sites(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_sites_label ON ct_sites(label);

-- Indexes for ct_simple_object_specs
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_contains_dataset ON ct_simple_object_specs(contains_dataset);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_documentation_object ON ct_simple_object_specs(has_documentation_object);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_specific_dataset_type ON ct_simple_object_specs(has_specific_dataset_type);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_format ON ct_simple_object_specs(has_format);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_encoding ON ct_simple_object_specs(has_encoding);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_data_theme ON ct_simple_object_specs(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_associated_project ON ct_simple_object_specs(has_associated_project);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_has_data_level ON ct_simple_object_specs(has_data_level);
CREATE INDEX IF NOT EXISTS idx_ct_simple_object_specs_label ON ct_simple_object_specs(label);

-- Indexes for ct_data_object_specs
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_contains_dataset ON ct_data_object_specs(contains_dataset);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_documentation_object ON ct_data_object_specs(has_documentation_object);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_specific_dataset_type ON ct_data_object_specs(has_specific_dataset_type);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_format ON ct_data_object_specs(has_format);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_encoding ON ct_data_object_specs(has_encoding);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_data_theme ON ct_data_object_specs(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_associated_project ON ct_data_object_specs(has_associated_project);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_has_data_level ON ct_data_object_specs(has_data_level);
CREATE INDEX IF NOT EXISTS idx_ct_data_object_specs_label ON ct_data_object_specs(label);

-- Indexes for ct_dataset_variables
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_has_value_type ON ct_dataset_variables(has_value_type);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_has_variable_title ON ct_dataset_variables(has_variable_title);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_label ON ct_dataset_variables(label);

-- Indexes for ct_as
CREATE INDEX IF NOT EXISTS idx_ct_as_has_documentation_object ON ct_as(has_documentation_object);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_webpage_elements ON ct_as(has_webpage_elements);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_responsible_organization ON ct_as(has_responsible_organization);
CREATE INDEX IF NOT EXISTS idx_ct_as_www_w3_org_ns_dcat_theme ON ct_as(www_w3_org_ns_dcat_theme);
CREATE INDEX IF NOT EXISTS idx_ct_as_country_code ON ct_as(country_code);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_atc_id ON ct_as(has_atc_id);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_elevation ON ct_as(has_elevation);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_latitude ON ct_as(has_latitude);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_longitude ON ct_as(has_longitude);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_name ON ct_as(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_station_id ON ct_as(has_station_id);
CREATE INDEX IF NOT EXISTS idx_ct_as_has_time_zone_offset ON ct_as(has_time_zone_offset);
CREATE INDEX IF NOT EXISTS idx_ct_as_label ON ct_as(label);

-- Indexes for ct_tabular_dataset_specs
CREATE INDEX IF NOT EXISTS idx_ct_tabular_dataset_specs_has_column ON ct_tabular_dataset_specs(has_column);
CREATE INDEX IF NOT EXISTS idx_ct_tabular_dataset_specs_label ON ct_tabular_dataset_specs(label);

-- Indexes for ct_plain_collections
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_is_next_version_of ON ct_plain_collections(is_next_version_of);
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_has_part ON ct_plain_collections(has_part);

-- Indexes for ct_funders
CREATE INDEX IF NOT EXISTS idx_ct_funders_has_etc_id ON ct_funders(has_etc_id);
CREATE INDEX IF NOT EXISTS idx_ct_funders_has_name ON ct_funders(has_name);

-- Indexes for ct_ecosystem_types
CREATE INDEX IF NOT EXISTS idx_ct_ecosystem_types_label ON ct_ecosystem_types(label);

-- Indexes for ct_webpage_elements
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_has_linkbox ON ct_webpage_elements(has_linkbox);
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_has_cover_image ON ct_webpage_elements(has_cover_image);
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_label ON ct_webpage_elements(label);
CREATE INDEX IF NOT EXISTS idx_ct_webpage_elements_comment ON ct_webpage_elements(comment);

-- Indexes for ct_climate_zones
CREATE INDEX IF NOT EXISTS idx_ct_climate_zones_label ON ct_climate_zones(label);

-- Indexes for ct_quantity_kinds
CREATE INDEX IF NOT EXISTS idx_ct_quantity_kinds_label ON ct_quantity_kinds(label);

-- Indexes for ct_object_formats
CREATE INDEX IF NOT EXISTS idx_ct_object_formats_see_also ON ct_object_formats(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_object_formats_label ON ct_object_formats(label);

-- Indexes for ct_os
CREATE INDEX IF NOT EXISTS idx_ct_os_has_webpage_elements ON ct_os(has_webpage_elements);
CREATE INDEX IF NOT EXISTS idx_ct_os_has_responsible_organization ON ct_os(has_responsible_organization);
CREATE INDEX IF NOT EXISTS idx_ct_os_has_spatial_coverage ON ct_os(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_os_www_w3_org_ns_dcat_theme ON ct_os(www_w3_org_ns_dcat_theme);
CREATE INDEX IF NOT EXISTS idx_ct_os_country_code ON ct_os(country_code);
CREATE INDEX IF NOT EXISTS idx_ct_os_has_name ON ct_os(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_os_has_otc_id ON ct_os(has_otc_id);
CREATE INDEX IF NOT EXISTS idx_ct_os_has_station_class ON ct_os(has_station_class);
CREATE INDEX IF NOT EXISTS idx_ct_os_has_station_id ON ct_os(has_station_id);
CREATE INDEX IF NOT EXISTS idx_ct_os_label ON ct_os(label);
CREATE INDEX IF NOT EXISTS idx_ct_os_www_w3_org_ns_dcat_contact_point ON ct_os(www_w3_org_ns_dcat_contact_point);
CREATE INDEX IF NOT EXISTS idx_ct_os_spatial ON ct_os(spatial);

-- Indexes for ct_projects
CREATE INDEX IF NOT EXISTS idx_ct_projects_label ON ct_projects(label);

-- Indexes for ct_value_formats
CREATE INDEX IF NOT EXISTS idx_ct_value_formats_label ON ct_value_formats(label);

-- Indexes for ct_dataset_specs
CREATE INDEX IF NOT EXISTS idx_ct_dataset_specs_has_variable ON ct_dataset_specs(has_variable);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_specs_label ON ct_dataset_specs(label);

-- Indexes for ct_data_themes
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_has_icon ON ct_data_themes(has_icon);
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_label ON ct_data_themes(label);

-- Indexes for ct_roles
CREATE INDEX IF NOT EXISTS idx_ct_roles_label ON ct_roles(label);

-- Indexes for ct_atmo_stations
CREATE INDEX IF NOT EXISTS idx_ct_atmo_stations_country_code ON ct_atmo_stations(country_code);
CREATE INDEX IF NOT EXISTS idx_ct_atmo_stations_has_elevation ON ct_atmo_stations(has_elevation);
CREATE INDEX IF NOT EXISTS idx_ct_atmo_stations_has_latitude ON ct_atmo_stations(has_latitude);
CREATE INDEX IF NOT EXISTS idx_ct_atmo_stations_has_longitude ON ct_atmo_stations(has_longitude);
CREATE INDEX IF NOT EXISTS idx_ct_atmo_stations_has_name ON ct_atmo_stations(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_atmo_stations_has_station_id ON ct_atmo_stations(has_station_id);

-- Indexes for ct_thematic_centers
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_data_theme ON ct_thematic_centers(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_name ON ct_thematic_centers(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_label ON ct_thematic_centers(label);

-- Indexes for ct_ingos_stations
CREATE INDEX IF NOT EXISTS idx_ct_ingos_stations_country_code ON ct_ingos_stations(country_code);
CREATE INDEX IF NOT EXISTS idx_ct_ingos_stations_has_elevation ON ct_ingos_stations(has_elevation);
CREATE INDEX IF NOT EXISTS idx_ct_ingos_stations_has_latitude ON ct_ingos_stations(has_latitude);
CREATE INDEX IF NOT EXISTS idx_ct_ingos_stations_has_longitude ON ct_ingos_stations(has_longitude);
CREATE INDEX IF NOT EXISTS idx_ct_ingos_stations_has_name ON ct_ingos_stations(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_ingos_stations_has_station_id ON ct_ingos_stations(has_station_id);

-- Indexes for ct_object_encodings
CREATE INDEX IF NOT EXISTS idx_ct_object_encodings_label ON ct_object_encodings(label);

-- Indexes for ct_central_facilities
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_has_name ON ct_central_facilities(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_label ON ct_central_facilities(label);

-- Indexes for ct_sail_drones
CREATE INDEX IF NOT EXISTS idx_ct_sail_drones_www_w3_org_ns_dcat_theme ON ct_sail_drones(www_w3_org_ns_dcat_theme);
CREATE INDEX IF NOT EXISTS idx_ct_sail_drones_country_code ON ct_sail_drones(country_code);
CREATE INDEX IF NOT EXISTS idx_ct_sail_drones_has_elevation ON ct_sail_drones(has_elevation);
CREATE INDEX IF NOT EXISTS idx_ct_sail_drones_has_name ON ct_sail_drones(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_sail_drones_has_station_id ON ct_sail_drones(has_station_id);

-- Indexes for ct_specific_dataset_types
CREATE INDEX IF NOT EXISTS idx_ct_specific_dataset_types_label ON ct_specific_dataset_types(label);

-- ======================================================================
-- POPULATE TABLES
-- ======================================================================

-- Populate ct_data_submissions
INSERT INTO ct_data_submissions (id
, ended_at_time
, started_at_time
, was_associated_with
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#endedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS ended_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#startedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS started_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#wasAssociatedWith' THEN obj ELSE NULL END) AS was_associated_with
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataSubmission'
)
GROUP BY subj;

-- Populate ct_data_objects
INSERT INTO ct_data_objects (id
, has_name
, has_object_spec
, has_sha256sum
, has_size_in_bytes
, was_submitted_by
, was_acquired_by
, has_number_of_rows
, was_produced_by
, is_next_version_of
, has_actual_column_names
, had_primary_source
, has_spatial_coverage
, has_actual_variable
, has_doi
, has_keywords
, contact_20_point
, contributor
, measurement_20_method
, measurement_20_scale
, measurement_20_unit
, observation_20_category
, parameter
, sampling_20_type
, time_20_interval
, has_end_time
, has_start_time
, has_temporal_resolution
, description
, title
, license
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec' THEN obj ELSE NULL END) AS has_object_spec
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum' THEN obj ELSE NULL END) AS has_sha256sum
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes' THEN obj::BIGINT ELSE NULL END) AS has_size_in_bytes
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy' THEN obj ELSE NULL END) AS was_submitted_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy' THEN obj ELSE NULL END) AS was_acquired_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNumberOfRows' THEN obj::INTEGER ELSE NULL END) AS has_number_of_rows
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy' THEN obj ELSE NULL END) AS was_produced_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN obj ELSE NULL END) AS is_next_version_of
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualColumnNames' THEN obj ELSE NULL END) AS has_actual_column_names
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#hadPrimarySource' THEN obj ELSE NULL END) AS had_primary_source
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN obj ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable' THEN obj ELSE NULL END) AS has_actual_variable
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi' THEN obj ELSE NULL END) AS has_doi
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/CONTACT%20POINT' THEN obj ELSE NULL END) AS contact_20_point
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/CONTRIBUTOR' THEN obj ELSE NULL END) AS contributor
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/MEASUREMENT%20METHOD' THEN obj ELSE NULL END) AS measurement_20_method
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/MEASUREMENT%20SCALE' THEN obj ELSE NULL END) AS measurement_20_scale
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/MEASUREMENT%20UNIT' THEN obj ELSE NULL END) AS measurement_20_unit
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/OBSERVATION%20CATEGORY' THEN obj ELSE NULL END) AS observation_20_category
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/PARAMETER' THEN obj ELSE NULL END) AS parameter
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/SAMPLING%20TYPE' THEN obj ELSE NULL END) AS sampling_20_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/resources/wdcgg/TIME%20INTERVAL' THEN obj ELSE NULL END) AS time_20_interval
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_end_time
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_start_time
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/license' THEN obj ELSE NULL END) AS license
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataObject'
)
GROUP BY subj;

-- Populate ct_data_acquisitions
INSERT INTO ct_data_acquisitions (id
, was_performed_with
, ended_at_time
, started_at_time
, was_associated_with
, has_sampling_height
, has_sampling_point
, was_performed_at
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith' THEN obj ELSE NULL END) AS was_performed_with
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#endedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS ended_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#startedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS started_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#wasAssociatedWith' THEN obj ELSE NULL END) AS was_associated_with
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingHeight' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_sampling_height
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingPoint' THEN obj ELSE NULL END) AS has_sampling_point
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedAt' THEN obj ELSE NULL END) AS was_performed_at
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataAcquisition'
)
GROUP BY subj;

-- Populate ct_data_productions
INSERT INTO ct_data_productions (id
, has_end_time
, was_performed_by
, was_hosted_by
, was_participated_in_by
, comment
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_end_time
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedBy' THEN obj ELSE NULL END) AS was_performed_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasHostedBy' THEN obj ELSE NULL END) AS was_hosted_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasParticipatedInBy' THEN obj ELSE NULL END) AS was_participated_in_by
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataProduction'
)
GROUP BY subj;

-- Populate ct_variable_infos
INSERT INTO ct_variable_infos (id
, label
, has_max_value
, has_min_value
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMaxValue' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_max_value
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMinValue' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_min_value
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/VariableInfo'
)
GROUP BY subj;

-- Populate ct_instruments
INSERT INTO ct_instruments (id
, has_model
, has_serial_number
, has_vendor
, www_w3_org_ns_ssn_has_deployment
, has_etc_id
, comment
, has_name
, has_atc_id
, has_instrument_owner
, has_instrument_component
, has_otc_id
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasModel' THEN obj ELSE NULL END) AS has_model
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSerialNumber' THEN obj ELSE NULL END) AS has_serial_number
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVendor' THEN obj ELSE NULL END) AS has_vendor
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/ssn/hasDeployment' THEN obj ELSE NULL END) AS www_w3_org_ns_ssn_has_deployment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasInstrumentOwner' THEN obj ELSE NULL END) AS has_instrument_owner
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasInstrumentComponent' THEN obj ELSE NULL END) AS has_instrument_component
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Instrument'
)
GROUP BY subj;

-- Populate ct_spatial_coverages
INSERT INTO ct_spatial_coverages (id
, as_geo_json
, label
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON' THEN obj ELSE NULL END) AS as_geo_json
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SpatialCoverage'
)
GROUP BY subj;

-- Populate ct_memberships
INSERT INTO ct_memberships (id
, label
, has_role
, at_organization
, has_start_time
, has_attribution_weight
, has_end_time
, has_extra_role_info
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasRole' THEN obj ELSE NULL END) AS has_role
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/atOrganization' THEN obj ELSE NULL END) AS at_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_start_time
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAttributionWeight' THEN obj::SMALLINT ELSE NULL END) AS has_attribution_weight
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_end_time
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasExtraRoleInfo' THEN obj ELSE NULL END) AS has_extra_role_info
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Membership'
)
GROUP BY subj;

-- Populate ct_persons
INSERT INTO ct_persons (id
, has_membership
, has_first_name
, has_last_name
, has_email
, has_etc_id
, has_orcid_id
, has_atc_id
, has_otc_id
, label
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership' THEN obj ELSE NULL END) AS has_membership
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFirstName' THEN obj ELSE NULL END) AS has_first_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLastName' THEN obj ELSE NULL END) AS has_last_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEmail' THEN obj ELSE NULL END) AS has_email
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOrcidId' THEN obj ELSE NULL END) AS has_orcid_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Person'
)
GROUP BY subj;

-- Populate ct_collections
INSERT INTO ct_collections (id
, has_part
, creator
, title
, description
, is_next_version_of
, has_doi
, has_spatial_coverage
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/hasPart' THEN obj ELSE NULL END) AS has_part
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/creator' THEN obj ELSE NULL END) AS creator
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN obj ELSE NULL END) AS is_next_version_of
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi' THEN obj ELSE NULL END) AS has_doi
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN obj ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Collection'
)
GROUP BY subj;

-- Populate ct_document_objects
INSERT INTO ct_document_objects (id
, has_name
, has_sha256sum
, has_size_in_bytes
, was_submitted_by
, creator
, title
, description
, is_next_version_of
, has_doi
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum' THEN obj ELSE NULL END) AS has_sha256sum
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes' THEN obj::BIGINT ELSE NULL END) AS has_size_in_bytes
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy' THEN obj ELSE NULL END) AS was_submitted_by
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/creator' THEN obj ELSE NULL END) AS creator
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN obj ELSE NULL END) AS is_next_version_of
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi' THEN obj ELSE NULL END) AS has_doi
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DocumentObject'
)
GROUP BY subj;

-- Populate ct_dataset_columns
INSERT INTO ct_dataset_columns (id
, has_column_title
, has_value_format
, has_value_type
, label
, is_optional_column
, is_regex_column
, comment
, is_quality_flag_for
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumnTitle' THEN obj ELSE NULL END) AS has_column_title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasValueFormat' THEN obj ELSE NULL END) AS has_value_format
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasValueType' THEN obj ELSE NULL END) AS has_value_type
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isOptionalColumn' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_optional_column
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isRegexColumn' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_regex_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isQualityFlagFor' THEN obj ELSE NULL END) AS is_quality_flag_for
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetColumn'
)
GROUP BY subj;

-- Populate ct_value_types
INSERT INTO ct_value_types (id
, label
, has_quantity_kind
, has_unit
, comment
, www_w3_org_2004_02_skos_core_exact_match
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasQuantityKind' THEN obj ELSE NULL END) AS has_quantity_kind
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasUnit' THEN obj ELSE NULL END) AS has_unit
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2004/02/skos/core#exactMatch' THEN obj ELSE NULL END) AS www_w3_org_2004_02_skos_core_exact_match
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ValueType'
)
GROUP BY subj;

-- Populate ct_lat_lon_boxes
INSERT INTO ct_lat_lon_boxes (id
, has_eastern_bound
, has_northern_bound
, has_southern_bound
, has_western_bound
, as_geo_json
, label
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEasternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_eastern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNorthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_northern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSouthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_southern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWesternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_western_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON' THEN obj ELSE NULL END) AS as_geo_json
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/LatLonBox'
)
GROUP BY subj;

-- Populate ct_es
INSERT INTO ct_es (id
, www_w3_org_ns_dcat_theme
, country_code
, has_etc_id
, has_latitude
, has_longitude
, has_name
, has_station_id
, label
, has_ecosystem_type
, has_time_zone_offset
, has_elevation
, has_mean_annual_precip
, has_mean_annual_temp
, comment
, has_funding
, has_station_class
, www_w3_org_ns_dcat_contact_point
, identifier
, is_part_of
, spatial
, subject
, title
, has_climate_zone
, has_depiction
, description
, has_mean_annual_radiation
, has_associated_publication
, has_documentation_object
, has_labeling_date
, has_spatial_coverage
, has_webpage_elements
, is_discontinued
, has_documentation_uri
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN obj ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) AS has_depiction
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) AS has_documentation_object
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN obj ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN obj ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ES'
)
GROUP BY subj;

-- Populate ct_organizations
INSERT INTO ct_organizations (id
, has_name
, label
, has_atc_id
, has_otc_id
, has_etc_id
, see_also
, has_email
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEmail' THEN obj ELSE NULL END) AS has_email
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Organization'
)
GROUP BY subj;

-- Populate ct_stations
INSERT INTO ct_stations (id
, has_name
, country
, has_latitude
, has_longitude
, country_code
, has_station_id
, has_elevation
, has_responsible_organization
, has_time_zone_offset
, label
, comment
, has_climate_zone
, has_documentation_uri
, has_spatial_coverage
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN obj ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN obj ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN obj ELSE NULL END) AS has_spatial_coverage
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Station'
)
GROUP BY subj;

-- Populate ct_positions
INSERT INTO ct_positions (id
, has_latitude
, has_longitude
, label
, as_geo_json
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON' THEN obj ELSE NULL END) AS as_geo_json
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Position'
)
GROUP BY subj;

-- Populate ct_link_boxes
INSERT INTO ct_link_boxes (id
, has_cover_image
, has_name
, has_order_weight
, label
, has_webpage_link
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasCoverImage' THEN obj ELSE NULL END) AS has_cover_image
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOrderWeight' THEN obj::SMALLINT ELSE NULL END) AS has_order_weight
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageLink' THEN obj ELSE NULL END) AS has_webpage_link
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/LinkBox'
)
GROUP BY subj;

-- Populate ct_fundings
INSERT INTO ct_fundings (id
, has_funder
, label
, has_end_date
, has_start_date
, award_title
, award_number
, award_uri
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunder' THEN obj ELSE NULL END) AS has_funder
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndDate' THEN obj::DATE ELSE NULL END) AS has_end_date
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartDate' THEN obj::DATE ELSE NULL END) AS has_start_date
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/awardTitle' THEN obj ELSE NULL END) AS award_title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/awardNumber' THEN obj ELSE NULL END) AS award_number
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/awardURI' THEN obj ELSE NULL END) AS award_uri
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Funding'
)
GROUP BY subj;

-- Populate ct_sites
INSERT INTO ct_sites (id
, has_sampling_point
, has_ecosystem_type
, has_spatial_coverage
, label
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingPoint' THEN obj ELSE NULL END) AS has_sampling_point
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN obj ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Site'
)
GROUP BY subj;

-- Populate ct_simple_object_specs
INSERT INTO ct_simple_object_specs (id
, has_associated_project
, has_data_level
, has_data_theme
, has_encoding
, has_format
, has_specific_dataset_type
, label
, contains_dataset
, has_keywords
, comment
, has_documentation_object
, implies_default_licence
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject' THEN obj ELSE NULL END) AS has_associated_project
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataLevel' THEN obj::SMALLINT ELSE NULL END) AS has_data_level
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataTheme' THEN obj ELSE NULL END) AS has_data_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEncoding' THEN obj ELSE NULL END) AS has_encoding
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFormat' THEN obj ELSE NULL END) AS has_format
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpecificDatasetType' THEN obj ELSE NULL END) AS has_specific_dataset_type
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/containsDataset' THEN obj ELSE NULL END) AS contains_dataset
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) AS has_documentation_object
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/impliesDefaultLicence' THEN obj ELSE NULL END) AS implies_default_licence
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SimpleObjectSpec'
)
GROUP BY subj;

-- Populate ct_data_object_specs
INSERT INTO ct_data_object_specs (id
, has_associated_project
, has_data_level
, has_data_theme
, has_encoding
, has_format
, has_specific_dataset_type
, label
, comment
, has_keywords
, contains_dataset
, see_also
, has_documentation_object
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject' THEN obj ELSE NULL END) AS has_associated_project
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataLevel' THEN obj::SMALLINT ELSE NULL END) AS has_data_level
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataTheme' THEN obj ELSE NULL END) AS has_data_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEncoding' THEN obj ELSE NULL END) AS has_encoding
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFormat' THEN obj ELSE NULL END) AS has_format
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpecificDatasetType' THEN obj ELSE NULL END) AS has_specific_dataset_type
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/containsDataset' THEN obj ELSE NULL END) AS contains_dataset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) AS has_documentation_object
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataObjectSpec'
)
GROUP BY subj;

-- Populate ct_dataset_variables
INSERT INTO ct_dataset_variables (id
, has_value_type
, has_variable_title
, label
, is_optional_variable
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasValueType' THEN obj ELSE NULL END) AS has_value_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariableTitle' THEN obj ELSE NULL END) AS has_variable_title
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isOptionalVariable' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_optional_variable
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetVariable'
)
GROUP BY subj;

-- Populate ct_as
INSERT INTO ct_as (id
, www_w3_org_ns_dcat_theme
, country_code
, has_atc_id
, has_elevation
, has_latitude
, has_longitude
, has_name
, has_responsible_organization
, has_station_id
, has_time_zone_offset
, label
, has_wigos_id
, has_station_class
, has_documentation_object
, has_depiction
, has_labeling_date
, www_w3_org_ns_dcat_contact_point
, identifier
, is_part_of
, spatial
, subject
, title
, has_webpage_elements
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN obj ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) AS has_documentation_object
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN obj ELSE NULL END) AS has_webpage_elements
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/AS'
)
GROUP BY subj;

-- Populate ct_tabular_dataset_specs
INSERT INTO ct_tabular_dataset_specs (id
, has_column
, label
, has_temporal_resolution
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn' THEN obj ELSE NULL END) AS has_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/TabularDatasetSpec'
)
GROUP BY subj;

-- Populate ct_plain_collections
INSERT INTO ct_plain_collections (id
, has_part
, is_next_version_of
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/hasPart' THEN obj ELSE NULL END) AS has_part
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN obj ELSE NULL END) AS is_next_version_of
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/PlainCollection'
)
GROUP BY subj;

-- Populate ct_funders
INSERT INTO ct_funders (id
, has_etc_id
, has_name
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Funder'
)
GROUP BY subj;

-- Populate ct_ecosystem_types
INSERT INTO ct_ecosystem_types (id
, label
, comment
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/EcosystemType'
)
GROUP BY subj;

-- Populate ct_webpage_elements
INSERT INTO ct_webpage_elements (id
, has_linkbox
, has_cover_image
, label
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLinkbox' THEN obj ELSE NULL END) AS has_linkbox
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasCoverImage' THEN obj ELSE NULL END) AS has_cover_image
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/WebpageElements'
)
GROUP BY subj;

-- Populate ct_climate_zones
INSERT INTO ct_climate_zones (id
, label
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ClimateZone'
)
GROUP BY subj;

-- Populate ct_quantity_kinds
INSERT INTO ct_quantity_kinds (id
, label
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/QuantityKind'
)
GROUP BY subj;

-- Populate ct_object_formats
INSERT INTO ct_object_formats (id
, label
, has_good_flag_value
, comment
, see_also
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasGoodFlagValue' THEN obj ELSE NULL END) AS has_good_flag_value
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ObjectFormat'
)
GROUP BY subj;

-- Populate ct_os
INSERT INTO ct_os (id
, www_w3_org_ns_dcat_theme
, country_code
, has_name
, has_otc_id
, has_responsible_organization
, has_station_class
, has_station_id
, label
, www_w3_org_ns_dcat_contact_point
, spatial
, identifier
, is_part_of
, subject
, title
, comment
, description
, has_spatial_coverage
, see_also
, has_latitude
, has_longitude
, has_labeling_date
, has_webpage_elements
, has_depiction
, is_discontinued
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN obj ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN obj ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN obj ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/OS'
)
GROUP BY subj;

-- Populate ct_projects
INSERT INTO ct_projects (id
, label
, comment
, see_also
, has_keywords
, has_hide_from_search_policy
, has_skip_pid_minting_policy
, has_skip_storage_policy
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasHideFromSearchPolicy' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS has_hide_from_search_policy
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSkipPidMintingPolicy' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS has_skip_pid_minting_policy
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSkipStoragePolicy' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS has_skip_storage_policy
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Project'
)
GROUP BY subj;

-- Populate ct_value_formats
INSERT INTO ct_value_formats (id
, label
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ValueFormat'
)
GROUP BY subj;

-- Populate ct_dataset_specs
INSERT INTO ct_dataset_specs (id
, has_variable
, label
, has_temporal_resolution
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable' THEN obj ELSE NULL END) AS has_variable
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetSpec'
)
GROUP BY subj;

-- Populate ct_data_themes
INSERT INTO ct_data_themes (id
, has_icon
, label
, has_marker_icon
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasIcon' THEN obj ELSE NULL END) AS has_icon
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMarkerIcon' THEN obj ELSE NULL END) AS has_marker_icon
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataTheme'
)
GROUP BY subj;

-- Populate ct_roles
INSERT INTO ct_roles (id
, label
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Role'
)
GROUP BY subj;

-- Populate ct_atmo_stations
INSERT INTO ct_atmo_stations (id
, country_code
, has_elevation
, has_latitude
, has_longitude
, has_name
, has_station_id
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/AtmoStation'
)
GROUP BY subj;

-- Populate ct_thematic_centers
INSERT INTO ct_thematic_centers (id
, has_name
, label
, has_data_theme
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataTheme' THEN obj ELSE NULL END) AS has_data_theme
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ThematicCenter'
)
GROUP BY subj;

-- Populate ct_ingos_stations
INSERT INTO ct_ingos_stations (id
, country_code
, has_elevation
, has_latitude
, has_longitude
, has_name
, has_station_id
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/IngosStation'
)
GROUP BY subj;

-- Populate ct_object_encodings
INSERT INTO ct_object_encodings (id
, label
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ObjectEncoding'
)
GROUP BY subj;

-- Populate ct_central_facilities
INSERT INTO ct_central_facilities (id
, has_name
, label
, comment
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/CentralFacility'
)
GROUP BY subj;

-- Populate ct_sail_drones
INSERT INTO ct_sail_drones (id
, www_w3_org_ns_dcat_theme
, country_code
, has_elevation
, has_name
, has_station_id
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SailDrone'
)
GROUP BY subj;

-- Populate ct_specific_dataset_types
INSERT INTO ct_specific_dataset_types (id
, label
)
SELECT
    subj AS id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SpecificDatasetType'
)
GROUP BY subj;
