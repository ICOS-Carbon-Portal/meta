-- Generated SQL for class-based tables (CREATE TABLES)
-- Source: class_predicates_analysis.json
-- Total tables: 14
-- Inlined tables: 22

-- Run foreign keys and indexes after creating tables:
-- 1. class_tables/create_class_tables.sql
-- 2. class_tables/create_foreign_keys.sql
-- 3. class_tables/create_indexes.sql
-- 4. class_tables/populate_class_tables.sql

-- Drop existing tables
DROP TABLE IF EXISTS ct_central_facilities CASCADE;
DROP TABLE IF EXISTS ct_collections CASCADE;
DROP TABLE IF EXISTS ct_data_objects CASCADE;
DROP TABLE IF EXISTS ct_data_submissions CASCADE;
DROP TABLE IF EXISTS ct_data_themes CASCADE;
DROP TABLE IF EXISTS ct_document_objects CASCADE;
DROP TABLE IF EXISTS ct_ecosystem_types CASCADE;
DROP TABLE IF EXISTS ct_organizations CASCADE;
DROP TABLE IF EXISTS ct_persons CASCADE;
DROP TABLE IF EXISTS ct_plain_collections CASCADE;
DROP TABLE IF EXISTS ct_spatial_coverages CASCADE;
DROP TABLE IF EXISTS ct_thematic_centers CASCADE;
DROP TABLE IF EXISTS ct_value_formats CASCADE;
DROP TABLE IF EXISTS ct_value_types CASCADE;

-- ======================================================================
-- CREATE TABLES
-- ======================================================================

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
-- Inlined tables: ct_object_specs, ct_data_acquisitions, ct_data_productions, ct_variable_infos

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
    see_also TEXT,
    was_acquired_by_was_performed_with TEXT,
    was_acquired_by_ended_at_time TIMESTAMP WITH TIME ZONE,
    was_acquired_by_started_at_time TIMESTAMP WITH TIME ZONE,
    was_acquired_by_was_associated_with TEXT,
    was_acquired_by_has_sampling_height DOUBLE PRECISION,
    was_acquired_by_has_sampling_point TEXT,
    was_acquired_by_was_performed_at TEXT,
    was_produced_by_has_end_time TIMESTAMP WITH TIME ZONE,
    was_produced_by_was_performed_by TEXT,
    was_produced_by_was_hosted_by TEXT,
    was_produced_by_was_participated_in_by TEXT,
    was_produced_by_comment TEXT,
    was_produced_by_see_also TEXT,
    has_actual_variable_label TEXT,
    has_actual_variable_has_max_value DOUBLE PRECISION,
    has_actual_variable_has_min_value DOUBLE PRECISION
);

-- Table: ct_persons
-- Class: cpmeta:Person (1,144 instances)
-- Inlined tables: ct_memberships

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
    comment TEXT,
    has_membership_label TEXT,
    has_membership_has_role TEXT,
    has_membership_at_organization TEXT,
    has_membership_has_start_time TIMESTAMP WITH TIME ZONE,
    has_membership_has_attribution_weight SMALLINT,
    has_membership_has_end_time TIMESTAMP WITH TIME ZONE,
    has_membership_has_extra_role_info TEXT
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

-- Table: ct_value_types
-- Class: cpmeta:ValueType (307 instances)
-- Inlined tables: ct_quantity_kinds

CREATE TABLE IF NOT EXISTS ct_value_types (
    id TEXT PRIMARY KEY,
    label TEXT,
    has_quantity_kind TEXT,
    has_unit TEXT,
    comment TEXT,
    www_w3_org_2004_02_skos_core_exact_match TEXT,
    see_also TEXT,
    has_quantity_kind_label TEXT,
    has_quantity_kind_comment TEXT
);

-- Table: ct_plain_collections
-- Class: cpmeta:PlainCollection (50 instances)

CREATE TABLE IF NOT EXISTS ct_plain_collections (
    id TEXT PRIMARY KEY,
    has_part TEXT,
    is_next_version_of TEXT
);

-- Table: ct_ecosystem_types
-- Class: cpmeta:EcosystemType (41 instances)

CREATE TABLE IF NOT EXISTS ct_ecosystem_types (
    id TEXT PRIMARY KEY,
    label TEXT,
    comment TEXT,
    see_also TEXT
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

-- Table: ct_thematic_centers
-- Class: cpmeta:ThematicCenter (4 instances)

CREATE TABLE IF NOT EXISTS ct_thematic_centers (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    label TEXT,
    has_data_theme TEXT
);

-- Table: ct_central_facilities
-- Class: cpmeta:CentralFacility (2 instances)

CREATE TABLE IF NOT EXISTS ct_central_facilities (
    id TEXT PRIMARY KEY,
    has_name TEXT,
    label TEXT,
    comment TEXT
);
