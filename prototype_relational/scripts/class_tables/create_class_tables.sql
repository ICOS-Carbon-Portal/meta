BEGIN;-- Generated SQL for class-based tables (SCHEMA)
-- Source: class_predicates_analysis.json
-- Total tables: 34
-- Foreign key constraints: ENABLED

-- Drop existing tables (in reverse dependency order)
DROP TABLE IF EXISTS ct_plain_collections CASCADE;
DROP TABLE IF EXISTS ct_collections CASCADE;
DROP TABLE IF EXISTS ct_static_objects CASCADE;
DROP TABLE IF EXISTS ct_object_specs CASCADE;
DROP TABLE IF EXISTS ct_dataset_specs CASCADE;
DROP TABLE IF EXISTS ct_data_acquisitions CASCADE;
DROP TABLE IF EXISTS ct_dataset_variables CASCADE;
DROP TABLE IF EXISTS ct_dataset_columns CASCADE;
DROP TABLE IF EXISTS ct_stations CASCADE;
DROP TABLE IF EXISTS ct_persons CASCADE;
DROP TABLE IF EXISTS ct_data_productions CASCADE;
DROP TABLE IF EXISTS ct_data_submissions CASCADE;
DROP TABLE IF EXISTS ct_value_types CASCADE;
DROP TABLE IF EXISTS ct_webpage_elements CASCADE;
DROP TABLE IF EXISTS ct_memberships CASCADE;
DROP TABLE IF EXISTS ct_fundings CASCADE;
DROP TABLE IF EXISTS ct_thematic_centers CASCADE;
DROP TABLE IF EXISTS ct_instruments CASCADE;
DROP TABLE IF EXISTS ct_object_formats CASCADE;
DROP TABLE IF EXISTS ct_quantity_kinds CASCADE;
DROP TABLE IF EXISTS ct_link_boxes CASCADE;
DROP TABLE IF EXISTS ct_roles CASCADE;
DROP TABLE IF EXISTS ct_ecosystem_types CASCADE;
DROP TABLE IF EXISTS ct_variable_infos CASCADE;
DROP TABLE IF EXISTS ct_funders CASCADE;
DROP TABLE IF EXISTS ct_spatial_coverages CASCADE;
DROP TABLE IF EXISTS ct_climate_zones CASCADE;
DROP TABLE IF EXISTS ct_projects CASCADE;
DROP TABLE IF EXISTS ct_data_themes CASCADE;
DROP TABLE IF EXISTS ct_organizations CASCADE;
DROP TABLE IF EXISTS ct_specific_dataset_types CASCADE;
DROP TABLE IF EXISTS ct_central_facilities CASCADE;
DROP TABLE IF EXISTS ct_object_encodings CASCADE;
DROP TABLE IF EXISTS ct_value_formats CASCADE;

-- ======================================================================
-- CREATE TABLES (in dependency order for inline foreign keys)
-- ======================================================================

-- Table: ct_value_formats
-- Class: cpmeta:ValueFormat (13 instances)

CREATE TABLE IF NOT EXISTS ct_value_formats (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_object_encodings
-- Class: cpmeta:ObjectEncoding (3 instances)

CREATE TABLE IF NOT EXISTS ct_object_encodings (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_central_facilities
-- Class: cpmeta:CentralFacility (2 instances)

CREATE TABLE IF NOT EXISTS ct_central_facilities (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_name TEXT,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_specific_dataset_types
-- Class: cpmeta:SpecificDatasetType (2 instances)

CREATE TABLE IF NOT EXISTS ct_specific_dataset_types (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_organizations
-- UNION TABLE merging: cpmeta:Organization
-- Class: MERGED:ct_organizations (256 instances)

CREATE TABLE IF NOT EXISTS ct_organizations (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    org_type TEXT NOT NULL CHECK (org_type IN ('organization', 'thematic_center', 'central_facility')),
    has_name TEXT,
    label TEXT,
    has_atc_id TEXT,
    has_otc_id TEXT,
    has_etc_id TEXT,
    see_also TEXT,
    has_email TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_data_themes
-- Class: cpmeta:DataTheme (4 instances)

CREATE TABLE IF NOT EXISTS ct_data_themes (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_icon TEXT,
    has_marker_icon TEXT,
    label TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_projects
-- Class: cpmeta:Project (12 instances)

CREATE TABLE IF NOT EXISTS ct_projects (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    comment TEXT,
    label TEXT,
    see_also TEXT,
    has_keywords TEXT,
    has_hide_from_search_policy BOOLEAN,
    has_skip_pid_minting_policy BOOLEAN,
    has_skip_storage_policy BOOLEAN,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_climate_zones
-- Class: cpmeta:ClimateZone (30 instances)

CREATE TABLE IF NOT EXISTS ct_climate_zones (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_spatial_coverages
-- UNION TABLE merging: cpmeta:SpatialCoverage, cpmeta:LatLonBox, cpmeta:Position
-- Class: MERGED:ct_spatial_coverages (4,164 instances)

CREATE TABLE IF NOT EXISTS ct_spatial_coverages (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    coverage_type TEXT NOT NULL CHECK (coverage_type IN ('spatial', 'latlon', 'position')),
    as_geo_json TEXT,
    label TEXT,
    has_eastern_bound DOUBLE PRECISION,
    has_northern_bound DOUBLE PRECISION,
    has_southern_bound DOUBLE PRECISION,
    has_western_bound DOUBLE PRECISION,
    has_latitude DOUBLE PRECISION,
    has_longitude DOUBLE PRECISION,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_funders
-- Class: cpmeta:Funder (47 instances)

CREATE TABLE IF NOT EXISTS ct_funders (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_etc_id TEXT,
    has_name TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_variable_infos
-- Class: cpmeta:VariableInfo (4,957 instances)

CREATE TABLE IF NOT EXISTS ct_variable_infos (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    has_max_value DOUBLE PRECISION,
    has_min_value DOUBLE PRECISION,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_ecosystem_types
-- Class: cpmeta:EcosystemType (17 instances)

CREATE TABLE IF NOT EXISTS ct_ecosystem_types (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_roles
-- Class: cpmeta:Role (5 instances)

CREATE TABLE IF NOT EXISTS ct_roles (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_link_boxes
-- Class: cpmeta:LinkBox (158 instances)

CREATE TABLE IF NOT EXISTS ct_link_boxes (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_cover_image TEXT,
    has_name TEXT,
    has_order_weight SMALLINT,
    label TEXT,
    has_webpage_link TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_quantity_kinds
-- Class: cpmeta:QuantityKind (21 instances)

CREATE TABLE IF NOT EXISTS ct_quantity_kinds (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_object_formats
-- Class: cpmeta:ObjectFormat (22 instances)

CREATE TABLE IF NOT EXISTS ct_object_formats (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    has_good_flag_value TEXT[],
    comment TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (see_also) REFERENCES ct_value_formats(id)
);

-- Table: ct_instruments
-- Class: cpmeta:Instrument (4,826 instances)

CREATE TABLE IF NOT EXISTS ct_instruments (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_model TEXT,
    has_serial_number TEXT,
    has_vendor TEXT,
    has_deployment TEXT[],
    has_etc_id TEXT,
    comment TEXT,
    has_name TEXT,
    has_atc_id TEXT,
    has_instrument_owner TEXT,
    has_instrument_component TEXT[],
    has_otc_id TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_vendor) REFERENCES ct_organizations(id),
    FOREIGN KEY (has_instrument_owner) REFERENCES ct_organizations(id)
);

-- Table: ct_thematic_centers
-- Class: cpmeta:ThematicCenter (3 instances)

CREATE TABLE IF NOT EXISTS ct_thematic_centers (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_data_theme TEXT,
    has_name TEXT,
    label TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id)
);

-- Table: ct_fundings
-- Class: cpmeta:Funding (115 instances)

CREATE TABLE IF NOT EXISTS ct_fundings (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_funder TEXT,
    label TEXT,
    has_end_date DATE,
    has_start_date DATE,
    award_title TEXT,
    award_number TEXT,
    comment TEXT,
    award_uri TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_funder) REFERENCES ct_funders(id)
);

-- Table: ct_memberships
-- Class: cpmeta:Membership (1,870 instances)

CREATE TABLE IF NOT EXISTS ct_memberships (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT[],
    has_role TEXT,
    at_organization TEXT,
    has_start_time TIMESTAMP WITH TIME ZONE,
    has_attribution_weight SMALLINT,
    has_end_time TIMESTAMP WITH TIME ZONE,
    has_extra_role_info TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_role) REFERENCES ct_roles(id),
    FOREIGN KEY (at_organization) REFERENCES ct_organizations(id)
);

-- Table: ct_webpage_elements
-- Class: cpmeta:WebpageElements (28 instances)

CREATE TABLE IF NOT EXISTS ct_webpage_elements (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_linkbox TEXT[],
    has_cover_image TEXT,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_value_types
-- Class: cpmeta:ValueType (166 instances)

CREATE TABLE IF NOT EXISTS ct_value_types (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    label TEXT,
    has_quantity_kind TEXT,
    has_unit TEXT,
    comment TEXT,
    exact_match TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_quantity_kind) REFERENCES ct_quantity_kinds(id)
);

-- Table: ct_data_submissions
-- Class: cpmeta:DataSubmission (2,344,302 instances)

CREATE TABLE IF NOT EXISTS ct_data_submissions (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    ended_at_time TIMESTAMP WITH TIME ZONE,
    started_at_time TIMESTAMP WITH TIME ZONE,
    was_associated_with TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (was_associated_with) REFERENCES ct_thematic_centers(id)
);

-- Table: ct_data_productions
-- Class: cpmeta:DataProduction (1,248,435 instances)

CREATE TABLE IF NOT EXISTS ct_data_productions (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_end_time TIMESTAMP WITH TIME ZONE,
    was_performed_by TEXT,
    was_hosted_by TEXT,
    was_participated_in_by TEXT[],
    comment TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (was_performed_by) REFERENCES ct_thematic_centers(id),
    FOREIGN KEY (was_hosted_by) REFERENCES ct_thematic_centers(id)
);

-- Table: ct_persons
-- Class: cpmeta:Person (1,146 instances)

CREATE TABLE IF NOT EXISTS ct_persons (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_membership TEXT[],
    has_first_name TEXT,
    has_last_name TEXT,
    has_email TEXT,
    has_etc_id TEXT,
    has_orcid_id TEXT,
    has_atc_id TEXT,
    has_otc_id TEXT,
    label TEXT,
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_stations
-- UNION TABLE merging: cpmeta:Station, cpmeta:AS, cpmeta:ES, cpmeta:OS, cpmeta:SailDrone, cpmeta:IngosStation, cpmeta:AtmoStation
-- Class: MERGED:ct_stations (623 instances)

CREATE TABLE IF NOT EXISTS ct_stations (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
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
    comment TEXT[],
    has_climate_zone TEXT,
    has_documentation_uri TEXT,
    has_spatial_coverage TEXT,
    theme TEXT[],
    has_atc_id TEXT,
    has_wigos_id TEXT,
    has_station_class TEXT,
    has_documentation_object TEXT[],
    has_depiction TEXT[],
    has_labeling_date DATE,
    contact_point TEXT[],
    identifier TEXT,
    is_part_of TEXT,
    spatial TEXT[],
    subject TEXT,
    title TEXT,
    has_webpage_elements TEXT,
    has_etc_id TEXT,
    has_ecosystem_type TEXT,
    has_mean_annual_precip DOUBLE PRECISION,
    has_mean_annual_temp DOUBLE PRECISION,
    has_funding TEXT[],
    description TEXT[],
    has_mean_annual_radiation DOUBLE PRECISION,
    has_associated_publication TEXT[],
    is_discontinued BOOLEAN,
    has_otc_id TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_responsible_organization) REFERENCES ct_organizations(id),
    FOREIGN KEY (has_climate_zone) REFERENCES ct_climate_zones(id),
    FOREIGN KEY (has_webpage_elements) REFERENCES ct_webpage_elements(id),
    FOREIGN KEY (has_ecosystem_type) REFERENCES ct_ecosystem_types(id),
    FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id)
);

-- Table: ct_dataset_columns
-- Class: cpmeta:DatasetColumn (270 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_columns (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_column_title TEXT,
    has_value_format TEXT,
    has_value_type TEXT,
    label TEXT,
    is_optional_column BOOLEAN,
    comment TEXT,
    is_regex_column BOOLEAN,
    is_quality_flag_for TEXT[],
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_value_format) REFERENCES ct_value_formats(id),
    FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id)
);

-- Table: ct_dataset_variables
-- Class: cpmeta:DatasetVariable (76 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_variables (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_value_type TEXT,
    has_variable_title TEXT,
    label TEXT,
    is_optional_variable BOOLEAN,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_value_type) REFERENCES ct_value_types(id)
);

-- Table: ct_data_acquisitions
-- Class: cpmeta:DataAcquisition (2,341,317 instances)

CREATE TABLE IF NOT EXISTS ct_data_acquisitions (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    was_performed_with TEXT[],
    ended_at_time TIMESTAMP WITH TIME ZONE,
    started_at_time TIMESTAMP WITH TIME ZONE,
    was_associated_with TEXT,
    has_sampling_height DOUBLE PRECISION,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (was_associated_with) REFERENCES ct_stations(id)
);

-- Table: ct_dataset_specs
-- UNION TABLE merging: cpmeta:DatasetSpec, cpmeta:TabularDatasetSpec
-- Class: MERGED:ct_dataset_specs (45 instances)

CREATE TABLE IF NOT EXISTS ct_dataset_specs (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    dataset_type TEXT NOT NULL CHECK (dataset_type IN ('dataset', 'tabular')),
    has_variable TEXT[],
    label TEXT,
    has_temporal_resolution TEXT,
    has_column TEXT[],
    comment TEXT,
    CHECK (prefix || id = rdf_subject)
);

-- Table: ct_object_specs
-- UNION TABLE merging: cpmeta:SimpleObjectSpec, cpmeta:DataObjectSpec
-- Class: MERGED:ct_object_specs (110 instances)

CREATE TABLE IF NOT EXISTS ct_object_specs (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    spec_type TEXT NOT NULL CHECK (spec_type IN ('simple', 'data')),
    contains_dataset TEXT,
    has_associated_project TEXT,
    has_data_level SMALLINT,
    has_data_theme TEXT,
    has_encoding TEXT,
    has_format TEXT,
    has_specific_dataset_type TEXT,
    label TEXT,
    has_keywords TEXT,
    comment TEXT[],
    has_documentation_object TEXT[],
    implies_default_licence TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id),
    FOREIGN KEY (has_encoding) REFERENCES ct_object_encodings(id),
    FOREIGN KEY (has_format) REFERENCES ct_object_formats(id),
    FOREIGN KEY (has_associated_project) REFERENCES ct_projects(id),
    FOREIGN KEY (has_specific_dataset_type) REFERENCES ct_specific_dataset_types(id),
    FOREIGN KEY (contains_dataset) REFERENCES ct_dataset_specs(id)
);

-- Table: ct_static_objects
-- UNION TABLE merging: cpmeta:DataObject, cpmeta:DocumentObject
-- Class: MERGED:ct_static_objects (2,344,302 instances)

CREATE TABLE IF NOT EXISTS ct_static_objects (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    object_type TEXT NOT NULL CHECK (object_type IN ('data', 'document')),
    has_name TEXT,
    has_object_spec TEXT,
    has_sha256sum TEXT,
    has_size_in_bytes BIGINT,
    was_submitted_by TEXT,
    was_acquired_by TEXT,
    has_number_of_rows INTEGER,
    was_produced_by TEXT,
    is_next_version_of TEXT[],
    has_actual_column_names TEXT,
    had_primary_source TEXT[],
    has_spatial_coverage TEXT,
    has_actual_variable TEXT[],
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
    see_also TEXT,
    creator TEXT[],
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id),
    FOREIGN KEY (was_acquired_by) REFERENCES ct_data_acquisitions(id),
    FOREIGN KEY (was_produced_by) REFERENCES ct_data_productions(id),
    FOREIGN KEY (has_object_spec) REFERENCES ct_object_specs(id),
    FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id)
);

-- Table: ct_collections
-- Class: cpmeta:Collection (778 instances)

CREATE TABLE IF NOT EXISTS ct_collections (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_part TEXT[],
    creator TEXT,
    title TEXT,
    description TEXT,
    is_next_version_of TEXT[],
    has_doi TEXT,
    has_spatial_coverage TEXT,
    see_also TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (creator) REFERENCES ct_central_facilities(id),
    FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id)
);

-- Table: ct_plain_collections
-- Class: cpmeta:PlainCollection (50 instances)

CREATE TABLE IF NOT EXISTS ct_plain_collections (
    id TEXT PRIMARY KEY,
    rdf_subject TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    has_part TEXT[],
    is_next_version_of TEXT,
    CHECK (prefix || id = rdf_subject),
    FOREIGN KEY (is_next_version_of) REFERENCES ct_static_objects(id)
);
COMMIT;
