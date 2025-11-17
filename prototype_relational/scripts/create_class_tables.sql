BEGIN;-- Generated SQL for class-based tables (SCHEMA)
-- Source: class_predicates_analysis.json
-- Total tables: 36

-- Drop existing tables
DROP TABLE IF EXISTS ct_central_facilities CASCADE;
DROP TABLE IF EXISTS ct_climate_zones CASCADE;
DROP TABLE IF EXISTS ct_collections CASCADE;
DROP TABLE IF EXISTS ct_data_acquisitions CASCADE;
DROP TABLE IF EXISTS ct_data_objects CASCADE;
DROP TABLE IF EXISTS ct_data_productions CASCADE;
DROP TABLE IF EXISTS ct_data_submissions CASCADE;
DROP TABLE IF EXISTS ct_data_themes CASCADE;
DROP TABLE IF EXISTS ct_dataset_columns CASCADE;
DROP TABLE IF EXISTS ct_dataset_specs CASCADE;
DROP TABLE IF EXISTS ct_dataset_variables CASCADE;
DROP TABLE IF EXISTS ct_document_objects CASCADE;
DROP TABLE IF EXISTS ct_ecosystem_types CASCADE;
DROP TABLE IF EXISTS ct_funders CASCADE;
DROP TABLE IF EXISTS ct_fundings CASCADE;
DROP TABLE IF EXISTS ct_instruments CASCADE;
DROP TABLE IF EXISTS ct_link_boxes CASCADE;
DROP TABLE IF EXISTS ct_memberships CASCADE;
DROP TABLE IF EXISTS ct_object_encodings CASCADE;
DROP TABLE IF EXISTS ct_object_formats CASCADE;
DROP TABLE IF EXISTS ct_object_specs CASCADE;
DROP TABLE IF EXISTS ct_organizations CASCADE;
DROP TABLE IF EXISTS ct_persons CASCADE;
DROP TABLE IF EXISTS ct_plain_collections CASCADE;
DROP TABLE IF EXISTS ct_projects CASCADE;
DROP TABLE IF EXISTS ct_quantity_kinds CASCADE;
DROP TABLE IF EXISTS ct_roles CASCADE;
DROP TABLE IF EXISTS ct_sites CASCADE;
DROP TABLE IF EXISTS ct_spatial_coverages CASCADE;
DROP TABLE IF EXISTS ct_specific_dataset_types CASCADE;
DROP TABLE IF EXISTS ct_stations CASCADE;
DROP TABLE IF EXISTS ct_thematic_centers CASCADE;
DROP TABLE IF EXISTS ct_value_formats CASCADE;
DROP TABLE IF EXISTS ct_value_types CASCADE;
DROP TABLE IF EXISTS ct_variable_infos CASCADE;
DROP TABLE IF EXISTS ct_webpage_elements CASCADE;

-- ======================================================================
-- CREATE TABLES
-- ======================================================================

-- Table: ct_object_specs
-- UNION TABLE merging: cpmeta:SimpleObjectSpec, cpmeta:DataObjectSpec
-- Class: MERGED:ct_object_specs (178 instances)

CREATE TABLE IF NOT EXISTS ct_object_specs (
    id TEXT PRIMARY KEY,
    spec_type TEXT NOT NULL CHECK (spec_type IN ('simple', 'data')),
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

-- Table: ct_spatial_coverages
-- UNION TABLE merging: cpmeta:SpatialCoverage, cpmeta:LatLonBox, cpmeta:Position
-- Class: MERGED:ct_spatial_coverages (4,466 instances)

CREATE TABLE IF NOT EXISTS ct_spatial_coverages (
    id TEXT PRIMARY KEY,
    coverage_type TEXT NOT NULL CHECK (coverage_type IN ('spatial', 'latlon', 'position')),
    as_geo_json TEXT,
    label TEXT,
    has_eastern_bound DOUBLE PRECISION,
    has_northern_bound DOUBLE PRECISION,
    has_southern_bound DOUBLE PRECISION,
    has_western_bound DOUBLE PRECISION,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION
);

-- Table: ct_organizations
-- UNION TABLE merging: cpmeta:Organization
-- Class: MERGED:ct_organizations (265 instances)

CREATE TABLE IF NOT EXISTS ct_organizations (
    id TEXT PRIMARY KEY,
    org_type TEXT NOT NULL CHECK (org_type IN ('organization', 'thematic_center', 'central_facility')),
    has_name TEXT,
    label TEXT,
    has_atc_id TEXT,
    has_otc_id TEXT,
    has_etc_id TEXT,
    see_also TEXT,
    has_email TEXT
);

-- Table: ct_stations
-- UNION TABLE merging: cpmeta:Station, cpmeta:AS, cpmeta:ES, cpmeta:OS, cpmeta:SailDrone, cpmeta:IngosStation, cpmeta:AtmoStation
-- Class: MERGED:ct_stations (628 instances)

CREATE TABLE IF NOT EXISTS ct_stations (
    id TEXT PRIMARY KEY,
    station_type TEXT NOT NULL CHECK (station_type IN ('station', 'as', 'es', 'os', 'saildrone', 'ingos', 'atmo')),
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
    has_spatial_coverage TEXT,
    www_w3_org_ns_dcat_theme TEXT,
    has_atc_id TEXT,
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
    has_webpage_elements TEXT,
    has_etc_id TEXT,
    has_ecosystem_type TEXT,
    has_mean_annual_precip DOUBLE PRECISION,
    has_mean_annual_temp DOUBLE PRECISION,
    has_funding TEXT,
    description TEXT,
    has_mean_annual_radiation DOUBLE PRECISION,
    has_associated_publication TEXT,
    is_discontinued BOOLEAN,
    has_otc_id TEXT,
    see_also TEXT
);

-- Table: ct_dataset_specs
-- UNION TABLE merging: cpmeta:DatasetSpec, cpmeta:TabularDatasetSpec
-- Class: MERGED:ct_dataset_specs (80 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_specs (
    id TEXT PRIMARY KEY,
    dataset_type TEXT NOT NULL CHECK (dataset_type IN ('dataset', 'tabular')),
    has_variable TEXT,
    label TEXT,
    has_temporal_resolution TEXT,
    has_column TEXT,
    comment TEXT
);

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

-- Table: ct_dataset_variables
-- Class: cpmeta:DatasetVariable (76 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_variables (
    id TEXT PRIMARY KEY,
    has_value_type TEXT,
    has_variable_title TEXT,
    label TEXT,
    is_optional_variable BOOLEAN
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

-- Table: ct_thematic_centers
-- Class: cpmeta:ThematicCenter (4 instances)

CREATE TABLE IF NOT EXISTS ct_thematic_centers (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    label TEXT,
    has_data_theme TEXT
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

-- Table: ct_specific_dataset_types
-- Class: cpmeta:SpecificDatasetType (2 instances)

CREATE TABLE IF NOT EXISTS ct_specific_dataset_types (
    id TEXT PRIMARY KEY,
    label TEXT
);

-- ======================================================================
-- FOREIGN KEY CONSTRAINTS
-- ======================================================================

-- Foreign keys for ct_collections
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_data_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_data_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_see_also FOREIGN KEY (see_also) REFERENCES ct_document_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_data_acquisitions
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_performed_with FOREIGN KEY (was_performed_with) REFERENCES ct_instruments(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_stations(id);
ALTER TABLE ct_data_acquisitions ADD CONSTRAINT fk_ct_data_acquisitions_was_performed_at FOREIGN KEY (was_performed_at) REFERENCES ct_sites(id);

-- Foreign keys for ct_data_objects
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_acquired_by FOREIGN KEY (was_acquired_by) REFERENCES ct_data_acquisitions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_produced_by FOREIGN KEY (was_produced_by) REFERENCES ct_data_productions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_object_spec FOREIGN KEY (has_object_spec) REFERENCES ct_object_specs(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_actual_variable FOREIGN KEY (has_actual_variable) REFERENCES ct_variable_infos(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_data_productions
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_performed_by FOREIGN KEY (was_performed_by) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_hosted_by FOREIGN KEY (was_hosted_by) REFERENCES ct_thematic_centers(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_was_participated_in_by FOREIGN KEY (was_participated_in_by) REFERENCES ct_organizations(id);
ALTER TABLE ct_data_productions ADD CONSTRAINT fk_ct_data_productions_see_also FOREIGN KEY (see_also) REFERENCES ct_document_objects(id);

-- Foreign keys for ct_data_submissions
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_thematic_centers(id);

-- Foreign keys for ct_dataset_columns
ALTER TABLE ct_dataset_columns ADD CONSTRAINT fk_ct_dataset_columns_has_value_format FOREIGN KEY (has_value_format) REFERENCES ct_value_formats(id);
ALTER TABLE ct_dataset_columns ADD CONSTRAINT fk_ct_dataset_columns_has_value_type FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id);

-- Foreign keys for ct_dataset_specs
ALTER TABLE ct_dataset_specs ADD CONSTRAINT fk_ct_dataset_specs_has_variable FOREIGN KEY (has_variable) REFERENCES ct_dataset_variables(id);
ALTER TABLE ct_dataset_specs ADD CONSTRAINT fk_ct_dataset_specs_has_column FOREIGN KEY (has_column) REFERENCES ct_dataset_columns(id);

-- Foreign keys for ct_dataset_variables
ALTER TABLE ct_dataset_variables ADD CONSTRAINT fk_ct_dataset_variables_has_value_type FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id);

-- Foreign keys for ct_document_objects
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);

-- Foreign keys for ct_fundings
ALTER TABLE ct_fundings ADD CONSTRAINT fk_ct_fundings_has_funder FOREIGN KEY (has_funder) REFERENCES ct_funders(id);

-- Foreign keys for ct_instruments
ALTER TABLE ct_instruments ADD CONSTRAINT fk_ct_instruments_has_vendor FOREIGN KEY (has_vendor) REFERENCES ct_organizations(id);
ALTER TABLE ct_instruments ADD CONSTRAINT fk_ct_instruments_has_instrument_owner FOREIGN KEY (has_instrument_owner) REFERENCES ct_organizations(id);

-- Foreign keys for ct_link_boxes
ALTER TABLE ct_link_boxes ADD CONSTRAINT fk_ct_link_boxes_has_webpage_link FOREIGN KEY (has_webpage_link) REFERENCES ct_document_objects(id);

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
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_has_documentation_object FOREIGN KEY (has_documentation_object) REFERENCES ct_document_objects(id);
ALTER TABLE ct_object_specs ADD CONSTRAINT fk_ct_object_specs_contains_dataset FOREIGN KEY (contains_dataset) REFERENCES ct_dataset_specs(id);

-- Foreign keys for ct_persons
ALTER TABLE ct_persons ADD CONSTRAINT fk_ct_persons_has_membership FOREIGN KEY (has_membership) REFERENCES ct_memberships(id);

-- Foreign keys for ct_plain_collections
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_data_objects(id);
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_data_objects(id);

-- Foreign keys for ct_sites
ALTER TABLE ct_sites ADD CONSTRAINT fk_ct_sites_has_ecosystem_type FOREIGN KEY (has_ecosystem_type) REFERENCES ct_ecosystem_types(id);
ALTER TABLE ct_sites ADD CONSTRAINT fk_ct_sites_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_stations
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_responsible_organization FOREIGN KEY (has_responsible_organization) REFERENCES ct_organizations(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_climate_zone FOREIGN KEY (has_climate_zone) REFERENCES ct_climate_zones(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_documentation_object FOREIGN KEY (has_documentation_object) REFERENCES ct_document_objects(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_webpage_elements FOREIGN KEY (has_webpage_elements) REFERENCES ct_webpage_elements(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_ecosystem_type FOREIGN KEY (has_ecosystem_type) REFERENCES ct_ecosystem_types(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_funding FOREIGN KEY (has_funding) REFERENCES ct_fundings(id);
ALTER TABLE ct_stations ADD CONSTRAINT fk_ct_stations_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_thematic_centers
ALTER TABLE ct_thematic_centers ADD CONSTRAINT fk_ct_thematic_centers_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);

-- Foreign keys for ct_value_types
ALTER TABLE ct_value_types ADD CONSTRAINT fk_ct_value_types_has_quantity_kind FOREIGN KEY (has_quantity_kind) REFERENCES ct_quantity_kinds(id);

-- Foreign keys for ct_webpage_elements
ALTER TABLE ct_webpage_elements ADD CONSTRAINT fk_ct_webpage_elements_has_linkbox FOREIGN KEY (has_linkbox) REFERENCES ct_link_boxes(id);

-- ======================================================================
-- INDEXES
-- ======================================================================

-- Indexes for ct_object_specs
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_documentation_object ON ct_object_specs(has_documentation_object);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_format ON ct_object_specs(has_format);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_specific_dataset_type ON ct_object_specs(has_specific_dataset_type);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_associated_project ON ct_object_specs(has_associated_project);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_data_theme ON ct_object_specs(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_has_encoding ON ct_object_specs(has_encoding);
CREATE INDEX IF NOT EXISTS idx_ct_object_specs_contains_dataset ON ct_object_specs(contains_dataset);
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
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_documentation_object ON ct_stations(has_documentation_object);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_funding ON ct_stations(has_funding);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_responsible_organization ON ct_stations(has_responsible_organization);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_spatial_coverage ON ct_stations(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_webpage_elements ON ct_stations(has_webpage_elements);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_climate_zone ON ct_stations(has_climate_zone);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_ecosystem_type ON ct_stations(has_ecosystem_type);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_name ON ct_stations(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_stations_country ON ct_stations(country);
CREATE INDEX IF NOT EXISTS idx_ct_stations_www_w3_org_ns_dcat_theme ON ct_stations(www_w3_org_ns_dcat_theme);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_atc_id ON ct_stations(has_atc_id);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_etc_id ON ct_stations(has_etc_id);
CREATE INDEX IF NOT EXISTS idx_ct_stations_has_otc_id ON ct_stations(has_otc_id);

-- Indexes for ct_dataset_specs
CREATE INDEX IF NOT EXISTS idx_ct_dataset_specs_has_column ON ct_dataset_specs(has_column);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_specs_has_variable ON ct_dataset_specs(has_variable);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_specs_label ON ct_dataset_specs(label);

-- Indexes for ct_data_submissions
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_was_associated_with ON ct_data_submissions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_ended_at_time ON ct_data_submissions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_submissions_started_at_time ON ct_data_submissions(started_at_time);

-- Indexes for ct_data_objects
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_object_spec ON ct_data_objects(has_object_spec);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_spatial_coverage ON ct_data_objects(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_actual_variable ON ct_data_objects(has_actual_variable);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_submitted_by ON ct_data_objects(was_submitted_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_produced_by ON ct_data_objects(was_produced_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_was_acquired_by ON ct_data_objects(was_acquired_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_name ON ct_data_objects(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_sha256sum ON ct_data_objects(has_sha256sum);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_size_in_bytes ON ct_data_objects(has_size_in_bytes);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_end_time ON ct_data_objects(has_end_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_objects_has_start_time ON ct_data_objects(has_start_time);

-- Indexes for ct_data_acquisitions
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_performed_with ON ct_data_acquisitions(was_performed_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_associated_with ON ct_data_acquisitions(was_associated_with);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_was_performed_at ON ct_data_acquisitions(was_performed_at);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_ended_at_time ON ct_data_acquisitions(ended_at_time);
CREATE INDEX IF NOT EXISTS idx_ct_data_acquisitions_started_at_time ON ct_data_acquisitions(started_at_time);

-- Indexes for ct_data_productions
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_see_also ON ct_data_productions(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_performed_by ON ct_data_productions(was_performed_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_hosted_by ON ct_data_productions(was_hosted_by);
CREATE INDEX IF NOT EXISTS idx_ct_data_productions_was_participated_in_by ON ct_data_productions(was_participated_in_by);
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
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_spatial_coverage ON ct_collections(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_collections_is_next_version_of ON ct_collections(is_next_version_of);
CREATE INDEX IF NOT EXISTS idx_ct_collections_see_also ON ct_collections(see_also);
CREATE INDEX IF NOT EXISTS idx_ct_collections_has_part ON ct_collections(has_part);
CREATE INDEX IF NOT EXISTS idx_ct_collections_creator ON ct_collections(creator);
CREATE INDEX IF NOT EXISTS idx_ct_collections_title ON ct_collections(title);
CREATE INDEX IF NOT EXISTS idx_ct_collections_description ON ct_collections(description);

-- Indexes for ct_document_objects
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_creator ON ct_document_objects(creator);
CREATE INDEX IF NOT EXISTS idx_ct_document_objects_was_submitted_by ON ct_document_objects(was_submitted_by);
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
CREATE INDEX IF NOT EXISTS idx_ct_sites_has_spatial_coverage ON ct_sites(has_spatial_coverage);
CREATE INDEX IF NOT EXISTS idx_ct_sites_has_ecosystem_type ON ct_sites(has_ecosystem_type);
CREATE INDEX IF NOT EXISTS idx_ct_sites_has_sampling_point ON ct_sites(has_sampling_point);
CREATE INDEX IF NOT EXISTS idx_ct_sites_label ON ct_sites(label);

-- Indexes for ct_dataset_variables
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_has_value_type ON ct_dataset_variables(has_value_type);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_has_variable_title ON ct_dataset_variables(has_variable_title);
CREATE INDEX IF NOT EXISTS idx_ct_dataset_variables_label ON ct_dataset_variables(label);

-- Indexes for ct_plain_collections
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_has_part ON ct_plain_collections(has_part);
CREATE INDEX IF NOT EXISTS idx_ct_plain_collections_is_next_version_of ON ct_plain_collections(is_next_version_of);

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

-- Indexes for ct_projects
CREATE INDEX IF NOT EXISTS idx_ct_projects_label ON ct_projects(label);

-- Indexes for ct_value_formats
CREATE INDEX IF NOT EXISTS idx_ct_value_formats_label ON ct_value_formats(label);

-- Indexes for ct_data_themes
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_has_icon ON ct_data_themes(has_icon);
CREATE INDEX IF NOT EXISTS idx_ct_data_themes_label ON ct_data_themes(label);

-- Indexes for ct_roles
CREATE INDEX IF NOT EXISTS idx_ct_roles_label ON ct_roles(label);

-- Indexes for ct_thematic_centers
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_data_theme ON ct_thematic_centers(has_data_theme);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_has_name ON ct_thematic_centers(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_thematic_centers_label ON ct_thematic_centers(label);

-- Indexes for ct_object_encodings
CREATE INDEX IF NOT EXISTS idx_ct_object_encodings_label ON ct_object_encodings(label);

-- Indexes for ct_central_facilities
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_has_name ON ct_central_facilities(has_name);
CREATE INDEX IF NOT EXISTS idx_ct_central_facilities_label ON ct_central_facilities(label);

-- Indexes for ct_specific_dataset_types
CREATE INDEX IF NOT EXISTS idx_ct_specific_dataset_types_label ON ct_specific_dataset_types(label);
COMMIT;