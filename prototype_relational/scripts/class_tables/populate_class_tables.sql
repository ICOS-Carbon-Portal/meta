-- Generated SQL for class-based tables (POPULATION)
-- Source: class_predicates_analysis.json
-- Triples table: rdf_triples
-- Total tables: 34

-- Run this script after creating tables with create_class_tables.sql

-- ======================================================================
-- POPULATE TABLES (in dependency order)
-- ======================================================================

-- Populate ct_value_formats
-- Class: cpmeta:ValueFormat (13 instances)
SELECT 'Populating ct_value_formats (13 instances)...' AS status;
INSERT OR IGNORE INTO ct_value_formats (id, rdf_subject, prefix
, label
, comment
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/ontologies/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ValueFormat'
)
GROUP BY subj;

-- Populate ct_object_encodings
-- Class: cpmeta:ObjectEncoding (3 instances)
SELECT 'Populating ct_object_encodings (3 instances)...' AS status;
INSERT OR IGNORE INTO ct_object_encodings (id, rdf_subject, prefix
, label
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/ontologies/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ObjectEncoding'
)
GROUP BY subj;

-- Populate ct_central_facilities
-- Class: cpmeta:CentralFacility (2 instances)
SELECT 'Populating ct_central_facilities (2 instances)...' AS status;
INSERT OR IGNORE INTO ct_central_facilities (id, rdf_subject, prefix
, has_name
, label
, comment
)
SELECT
    SUBSTRING(subj FROM 48) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/organizations/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/CentralFacility'
)
GROUP BY subj;

-- Populate ct_specific_dataset_types
-- Class: cpmeta:SpecificDatasetType (2 instances)
SELECT 'Populating ct_specific_dataset_types (2 instances)...' AS status;
INSERT OR IGNORE INTO ct_specific_dataset_types (id, rdf_subject, prefix
, label
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/ontologies/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SpecificDatasetType'
)
GROUP BY subj;

-- Populate ct_organizations
-- UNION TABLE merging: cpmeta:Organization
-- Class: MERGED:ct_organizations (256 instances)
SELECT 'Populating ct_organizations (256 instances)...' AS status;
INSERT OR IGNORE INTO ct_organizations (id, rdf_subject, prefix, org_type
, has_name
, label
, has_atc_id
, has_otc_id
, has_etc_id
, see_also
, has_email
)
SELECT
    SUBSTRING(subj FROM 48) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/organizations/') AS prefix
    , 'organization' AS org_type
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
GROUP BY subj
;

-- Populate ct_data_themes
-- Class: cpmeta:DataTheme (4 instances)
SELECT 'Populating ct_data_themes (4 instances)...' AS status;
INSERT OR IGNORE INTO ct_data_themes (id, rdf_subject, prefix
, has_icon
, has_marker_icon
, label
)
SELECT
    SUBSTRING(subj FROM 40) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/themes') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasIcon' THEN obj ELSE NULL END) AS has_icon
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMarkerIcon' THEN obj ELSE NULL END) AS has_marker_icon
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataTheme'
)
GROUP BY subj;

-- Populate ct_projects
-- Class: cpmeta:Project (12 instances)
SELECT 'Populating ct_projects (12 instances)...' AS status;
INSERT OR IGNORE INTO ct_projects (id, rdf_subject, prefix
, comment
, label
, see_also
, has_keywords
, has_hide_from_search_policy
, has_skip_pid_minting_policy
, has_skip_storage_policy
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/projects') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasHideFromSearchPolicy' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS has_hide_from_search_policy
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSkipPidMintingPolicy' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS has_skip_pid_minting_policy
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSkipStoragePolicy' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS has_skip_storage_policy
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Project'
)
GROUP BY subj;

-- Populate ct_climate_zones
-- Class: cpmeta:ClimateZone (30 instances)
SELECT 'Populating ct_climate_zones (30 instances)...' AS status;
INSERT OR IGNORE INTO ct_climate_zones (id, rdf_subject, prefix
, label
, see_also
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/ontologies/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ClimateZone'
)
GROUP BY subj;

-- Populate ct_spatial_coverages
-- UNION TABLE merging: cpmeta:SpatialCoverage, cpmeta:LatLonBox, cpmeta:Position
-- Class: MERGED:ct_spatial_coverages (4,164 instances)
SELECT 'Populating ct_spatial_coverages (4,164 instances)...' AS status;
INSERT OR IGNORE INTO ct_spatial_coverages (id, rdf_subject, prefix, coverage_type
, as_geo_json
, label
, has_eastern_bound
, has_northern_bound
, has_southern_bound
, has_western_bound
, has_latitude
, has_longitude
)
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/latlonboxes'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/position_'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/spcov_'
        END) AS prefix
    , 'spatial' AS coverage_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON' THEN obj ELSE NULL END) AS as_geo_json
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEasternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_eastern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNorthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_northern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSouthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_southern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWesternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_western_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SpatialCoverage'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/latlonboxes'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/position_'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/spcov_'
        END) AS prefix
    , 'latlon' AS coverage_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON' THEN obj ELSE NULL END) AS as_geo_json
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEasternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_eastern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNorthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_northern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSouthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_southern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWesternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_western_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/LatLonBox'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/latlonboxes'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/position_'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/spcov_'
        END) AS prefix
    , 'position' AS coverage_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON' THEN obj ELSE NULL END) AS as_geo_json
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEasternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_eastern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNorthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_northern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSouthernBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_southern_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWesternBound' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_western_bound
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Position'
)
GROUP BY subj
;

-- Populate ct_funders
-- Class: cpmeta:Funder (47 instances)
SELECT 'Populating ct_funders (47 instances)...' AS status;
INSERT OR IGNORE INTO ct_funders (id, rdf_subject, prefix
, has_etc_id
, has_name
)
SELECT
    SUBSTRING(subj FROM 48) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/organizations/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Funder'
)
GROUP BY subj;

-- Populate ct_variable_infos
-- Class: cpmeta:VariableInfo (4,957 instances)
SELECT 'Populating ct_variable_infos (4,957 instances)...' AS status;
INSERT OR IGNORE INTO ct_variable_infos (id, rdf_subject, prefix
, label
, has_max_value
, has_min_value
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/varinfo_') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMaxValue' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_max_value
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMinValue' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_min_value
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/VariableInfo'
)
GROUP BY subj;

-- Populate ct_ecosystem_types
-- Class: cpmeta:EcosystemType (17 instances)
SELECT 'Populating ct_ecosystem_types (17 instances)...' AS status;
INSERT OR IGNORE INTO ct_ecosystem_types (id, rdf_subject, prefix
, label
, comment
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/ontologies/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/EcosystemType'
)
GROUP BY subj;

-- Populate ct_roles
-- Class: cpmeta:Role (5 instances)
SELECT 'Populating ct_roles (5 instances)...' AS status;
INSERT OR IGNORE INTO ct_roles (id, rdf_subject, prefix
, label
, comment
)
SELECT
    SUBSTRING(subj FROM 40) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/roles/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Role'
)
GROUP BY subj;

-- Populate ct_link_boxes
-- Class: cpmeta:LinkBox (158 instances)
SELECT 'Populating ct_link_boxes (158 instances)...' AS status;
INSERT OR IGNORE INTO ct_link_boxes (id, rdf_subject, prefix
, has_cover_image
, has_name
, has_order_weight
, label
, has_webpage_link
)
SELECT
    SUBSTRING(subj FROM 39) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/icos/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasCoverImage' THEN obj ELSE NULL END) AS has_cover_image
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOrderWeight' THEN obj::SMALLINT ELSE NULL END) AS has_order_weight
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageLink' THEN obj ELSE NULL END) AS has_webpage_link
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/LinkBox'
)
GROUP BY subj;

-- Populate ct_quantity_kinds
-- Class: cpmeta:QuantityKind (21 instances)
SELECT 'Populating ct_quantity_kinds (21 instances)...' AS status;
INSERT OR IGNORE INTO ct_quantity_kinds (id, rdf_subject, prefix
, label
, comment
)
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/QuantityKind'
)
GROUP BY subj;

-- Populate ct_object_formats
-- Class: cpmeta:ObjectFormat (22 instances)
SELECT 'Populating ct_object_formats (22 instances)...' AS status;
INSERT OR IGNORE INTO ct_object_formats (id, rdf_subject, prefix
, label
, has_good_flag_value
, comment
, see_also
)
SELECT
    SUBSTRING(subj FROM 42) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/ontologies/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasGoodFlagValue' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasGoodFlagValue') AS has_good_flag_value
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ObjectFormat'
)
GROUP BY subj;

-- Populate ct_instruments
-- Class: cpmeta:Instrument (4,826 instances)
SELECT 'Populating ct_instruments (4,826 instances)...' AS status;
INSERT OR IGNORE INTO ct_instruments (id, rdf_subject, prefix
, has_model
, has_serial_number
, has_vendor
, has_deployment
, has_etc_id
, comment
, has_name
, has_atc_id
, has_instrument_owner
, has_instrument_component
, has_otc_id
)
SELECT
    SUBSTRING(subj FROM 46) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/instruments/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasModel' THEN obj ELSE NULL END) AS has_model
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSerialNumber' THEN obj ELSE NULL END) AS has_serial_number
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVendor' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_vendor
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/ssn/hasDeployment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/ssn/hasDeployment') AS has_deployment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasInstrumentOwner' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_instrument_owner
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasInstrumentComponent' THEN SUBSTRING(obj FROM 46) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasInstrumentComponent') AS has_instrument_component
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Instrument'
)
GROUP BY subj;

-- Populate ct_thematic_centers
-- Class: cpmeta:ThematicCenter (3 instances)
SELECT 'Populating ct_thematic_centers (3 instances)...' AS status;
INSERT OR IGNORE INTO ct_thematic_centers (id, rdf_subject, prefix
, has_data_theme
, has_name
, label
)
SELECT
    SUBSTRING(subj FROM 48) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/organizations/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataTheme' THEN SUBSTRING(obj FROM 40) ELSE NULL END) AS has_data_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ThematicCenter'
)
GROUP BY subj;

-- Populate ct_fundings
-- Class: cpmeta:Funding (115 instances)
SELECT 'Populating ct_fundings (115 instances)...' AS status;
INSERT OR IGNORE INTO ct_fundings (id, rdf_subject, prefix
, has_funder
, label
, has_end_date
, has_start_date
, award_title
, award_number
, comment
, award_uri
)
SELECT
    SUBSTRING(subj FROM 43) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/fundings/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunder' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_funder
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndDate' THEN obj::DATE ELSE NULL END) AS has_end_date
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartDate' THEN obj::DATE ELSE NULL END) AS has_start_date
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/awardTitle' THEN obj ELSE NULL END) AS award_title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/awardNumber' THEN obj ELSE NULL END) AS award_number
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/awardURI' THEN obj ELSE NULL END) AS award_uri
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Funding'
)
GROUP BY subj;

-- Populate ct_memberships
-- Class: cpmeta:Membership (1,870 instances)
SELECT 'Populating ct_memberships (1,870 instances)...' AS status;
INSERT OR IGNORE INTO ct_memberships (id, rdf_subject, prefix
, label
, has_role
, at_organization
, has_start_time
, has_attribution_weight
, has_end_time
, has_extra_role_info
)
SELECT
    SUBSTRING(subj FROM 46) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/memberships/') AS prefix
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#label') AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasRole' THEN SUBSTRING(obj FROM 40) ELSE NULL END) AS has_role
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/atOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS at_organization
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

-- Populate ct_webpage_elements
-- Class: cpmeta:WebpageElements (28 instances)
SELECT 'Populating ct_webpage_elements (28 instances)...' AS status;
INSERT OR IGNORE INTO ct_webpage_elements (id, rdf_subject, prefix
, has_linkbox
, has_cover_image
, label
, comment
)
SELECT
    SUBSTRING(subj FROM 39) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/icos/') AS prefix
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLinkbox' THEN SUBSTRING(obj FROM 39) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLinkbox') AS has_linkbox
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasCoverImage' THEN obj ELSE NULL END) AS has_cover_image
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/WebpageElements'
)
GROUP BY subj;

-- Populate ct_value_types
-- Class: cpmeta:ValueType (166 instances)
SELECT 'Populating ct_value_types (166 instances)...' AS status;
INSERT OR IGNORE INTO ct_value_types (id, rdf_subject, prefix
, label
, has_quantity_kind
, has_unit
, comment
, exact_match
, see_also
)
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasQuantityKind' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS has_quantity_kind
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasUnit' THEN obj ELSE NULL END) AS has_unit
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2004/02/skos/core#exactMatch' THEN obj ELSE NULL END) AS exact_match
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ValueType'
)
GROUP BY subj;

-- Populate ct_data_submissions
-- Class: cpmeta:DataSubmission (2,344,302 instances)
SELECT 'Populating ct_data_submissions (2,344,302 instances)...' AS status;
INSERT OR IGNORE INTO ct_data_submissions (id, rdf_subject, prefix
, ended_at_time
, started_at_time
, was_associated_with
)
SELECT
    SUBSTRING(subj FROM 39) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/subm_') AS prefix
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#endedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS ended_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#startedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS started_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#wasAssociatedWith' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS was_associated_with
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataSubmission'
)
GROUP BY subj;

-- Populate ct_data_productions
-- Class: cpmeta:DataProduction (1,248,435 instances)
SELECT 'Populating ct_data_productions (1,248,435 instances)...' AS status;
INSERT OR IGNORE INTO ct_data_productions (id, rdf_subject, prefix
, has_end_time
, was_performed_by
, was_hosted_by
, was_participated_in_by
, comment
, see_also
)
SELECT
    SUBSTRING(subj FROM 39) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/prod_') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_end_time
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedBy' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS was_performed_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasHostedBy' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS was_hosted_by
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasParticipatedInBy' THEN SUBSTRING(obj FROM 48) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasParticipatedInBy') AS was_participated_in_by
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataProduction'
)
GROUP BY subj;

-- Populate ct_persons
-- Class: cpmeta:Person (1,146 instances)
SELECT 'Populating ct_persons (1,146 instances)...' AS status;
INSERT OR IGNORE INTO ct_persons (id, rdf_subject, prefix
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
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/people/') AS prefix
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership' THEN SUBSTRING(obj FROM 46) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership') AS has_membership
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

-- Populate ct_stations
-- UNION TABLE merging: cpmeta:Station, cpmeta:AS, cpmeta:ES, cpmeta:OS, cpmeta:SailDrone, cpmeta:IngosStation, cpmeta:AtmoStation
-- Class: MERGED:ct_stations (623 instances)
SELECT 'Populating ct_stations (623 instances)...' AS status;
INSERT OR IGNORE INTO ct_stations (id, rdf_subject, prefix, station_type
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
, theme
, has_atc_id
, has_wigos_id
, has_station_class
, has_documentation_object
, has_depiction
, has_labeling_date
, contact_point
, identifier
, is_part_of
, spatial
, subject
, title
, has_webpage_elements
, has_etc_id
, has_ecosystem_type
, has_mean_annual_precip
, has_mean_annual_temp
, has_funding
, description
, has_mean_annual_radiation
, has_associated_publication
, is_discontinued
, has_otc_id
, see_also
)
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'station' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Station'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'as' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/AS'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'es' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ES'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'os' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/OS'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'saildrone' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SailDrone'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'ingos' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/IngosStation'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM (CASE
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) AS id
    , subj AS rdf_subject
    , (    CASE
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/station/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/stations/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/wdcgg/'
            WHEN subj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 'http://meta.icos-cp.eu/resources/icos/'
        END) AS prefix
    , 'atmo' AS station_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/country' THEN obj ELSE NULL END) AS country
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLatitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_latitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLongitude' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_longitude
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/countryCode' THEN obj ELSE NULL END) AS country_code
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationId' THEN obj ELSE NULL END) AS has_station_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasElevation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_elevation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasResponsibleOrganization' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS has_responsible_organization
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTimeZoneOffset' THEN obj::SMALLINT ELSE NULL END) AS has_time_zone_offset
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasClimateZone' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_climate_zone
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationUri' THEN obj ELSE NULL END) AS has_documentation_uri
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#theme') AS theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWigosId' THEN obj ELSE NULL END) AS has_wigos_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStationClass' THEN obj ELSE NULL END) AS has_station_class
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDepiction') AS has_depiction
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLabelingDate' THEN obj::DATE ELSE NULL END) AS has_labeling_date
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/dcat#contactPoint' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/dcat#contactPoint') AS contact_point
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/identifier' THEN obj ELSE NULL END) AS identifier
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/isPartOf' THEN obj ELSE NULL END) AS is_part_of
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/spatial' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/spatial') AS spatial
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/subject' THEN obj ELSE NULL END) AS subject
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasWebpageElements' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS has_webpage_elements
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN SUBSTRING(obj FROM 43) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding') AS has_funding
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/description') AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication') AS has_associated_publication
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/AtmoStation'
)
GROUP BY subj
;

-- Populate ct_dataset_columns
-- Class: cpmeta:DatasetColumn (270 instances)
SELECT 'Populating ct_dataset_columns (270 instances)...' AS status;
INSERT OR IGNORE INTO ct_dataset_columns (id, rdf_subject, prefix
, has_column_title
, has_value_format
, has_value_type
, label
, is_optional_column
, comment
, is_regex_column
, is_quality_flag_for
, see_also
)
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumnTitle' THEN obj ELSE NULL END) AS has_column_title
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasValueFormat' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_value_format
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasValueType' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS has_value_type
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isOptionalColumn' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_optional_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isRegexColumn' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_regex_column
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isQualityFlagFor' THEN SUBSTRING(obj FROM 41) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isQualityFlagFor') AS is_quality_flag_for
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetColumn'
)
GROUP BY subj;

-- Populate ct_dataset_variables
-- Class: cpmeta:DatasetVariable (76 instances)
SELECT 'Populating ct_dataset_variables (76 instances)...' AS status;
INSERT OR IGNORE INTO ct_dataset_variables (id, rdf_subject, prefix
, has_value_type
, has_variable_title
, label
, is_optional_variable
)
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasValueType' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS has_value_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariableTitle' THEN obj ELSE NULL END) AS has_variable_title
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , BOOL_OR(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isOptionalVariable' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_optional_variable
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetVariable'
)
GROUP BY subj;

-- Populate ct_data_acquisitions
-- Class: cpmeta:DataAcquisition (2,341,317 instances)
SELECT 'Populating ct_data_acquisitions (2,341,317 instances)...' AS status;
INSERT OR IGNORE INTO ct_data_acquisitions (id, rdf_subject, prefix
, was_performed_with
, ended_at_time
, started_at_time
, was_associated_with
, has_sampling_height
)
SELECT
    SUBSTRING(subj FROM 38) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/acq_') AS prefix
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith' THEN SUBSTRING(obj FROM 46) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith') AS was_performed_with
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#endedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS ended_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#startedAtTime' THEN obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS started_at_time
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/prov#wasAssociatedWith' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%' ESCAPE '\' THEN 48
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/stations/%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%' ESCAPE '\' THEN 40
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/icos/%' ESCAPE '\' THEN 39
    END)) ELSE NULL END) AS was_associated_with
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingHeight' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_sampling_height
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataAcquisition'
)
GROUP BY subj;

-- Populate ct_dataset_specs
-- UNION TABLE merging: cpmeta:DatasetSpec, cpmeta:TabularDatasetSpec
-- Class: MERGED:ct_dataset_specs (45 instances)
SELECT 'Populating ct_dataset_specs (45 instances)...' AS status;
INSERT OR IGNORE INTO ct_dataset_specs (id, rdf_subject, prefix, dataset_type
, has_variable
, label
, has_temporal_resolution
, has_column
, comment
)
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , 'dataset' AS dataset_type
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable' THEN SUBSTRING(obj FROM 41) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable') AS has_variable
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn' THEN SUBSTRING(obj FROM 41) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn') AS has_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetSpec'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , 'tabular' AS dataset_type
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable' THEN SUBSTRING(obj FROM 41) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable') AS has_variable
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn' THEN SUBSTRING(obj FROM 41) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn') AS has_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/TabularDatasetSpec'
)
GROUP BY subj
;

-- Populate ct_object_specs
-- UNION TABLE merging: cpmeta:SimpleObjectSpec, cpmeta:DataObjectSpec
-- Class: MERGED:ct_object_specs (110 instances)
SELECT 'Populating ct_object_specs (110 instances)...' AS status;
INSERT OR IGNORE INTO ct_object_specs (id, rdf_subject, prefix, spec_type
, contains_dataset
, has_associated_project
, has_data_level
, has_data_theme
, has_encoding
, has_format
, has_specific_dataset_type
, label
, has_keywords
, comment
, has_documentation_object
, implies_default_licence
, see_also
)
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , 'simple' AS spec_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/containsDataset' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS contains_dataset
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_associated_project
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataLevel' THEN obj::SMALLINT ELSE NULL END) AS has_data_level
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataTheme' THEN SUBSTRING(obj FROM 40) ELSE NULL END) AS has_data_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEncoding' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_encoding
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFormat' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_format
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpecificDatasetType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_specific_dataset_type
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/impliesDefaultLicence' THEN obj ELSE NULL END) AS implies_default_licence
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/SimpleObjectSpec'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM 41) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/cpmeta/') AS prefix
    , 'data' AS spec_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/containsDataset' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS contains_dataset
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_associated_project
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataLevel' THEN obj::SMALLINT ELSE NULL END) AS has_data_level
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDataTheme' THEN SUBSTRING(obj FROM 40) ELSE NULL END) AS has_data_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEncoding' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_encoding
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFormat' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_format
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpecificDatasetType' THEN SUBSTRING(obj FROM 42) ELSE NULL END) AS has_specific_dataset_type
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN obj ELSE NULL END) AS has_keywords
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/2000/01/rdf-schema#comment') AS comment
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject' THEN obj ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDocumentationObject') AS has_documentation_object
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/impliesDefaultLicence' THEN obj ELSE NULL END) AS implies_default_licence
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataObjectSpec'
)
GROUP BY subj
;

-- Populate ct_static_objects
-- UNION TABLE merging: cpmeta:DataObject, cpmeta:DocumentObject
-- Class: MERGED:ct_static_objects (2,344,302 instances)
SELECT 'Populating ct_static_objects (2,344,302 instances)...' AS status;
INSERT OR IGNORE INTO ct_static_objects (id, rdf_subject, prefix, object_type
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
, creator
)
SELECT
    SUBSTRING(subj FROM 33) AS id
    , subj AS rdf_subject
    , (    'https://meta.icos-cp.eu/objects/') AS prefix
    , 'data' AS object_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS has_object_spec
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum' THEN obj ELSE NULL END) AS has_sha256sum
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes' THEN obj::BIGINT ELSE NULL END) AS has_size_in_bytes
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS was_submitted_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy' THEN SUBSTRING(obj FROM 38) ELSE NULL END) AS was_acquired_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNumberOfRows' THEN obj::INTEGER ELSE NULL END) AS has_number_of_rows
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS was_produced_by
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN SUBSTRING(obj FROM 33) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf') AS is_next_version_of
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualColumnNames' THEN obj ELSE NULL END) AS has_actual_column_names
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/prov#hadPrimarySource' THEN SUBSTRING(obj FROM 33) ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/prov#hadPrimarySource') AS had_primary_source
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable' THEN SUBSTRING(obj FROM 42) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable') AS has_actual_variable
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
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/creator' THEN SUBSTRING(obj FROM 48) ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/creator') AS creator
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataObject'
)
GROUP BY subj
UNION ALL
SELECT
    SUBSTRING(subj FROM 33) AS id
    , subj AS rdf_subject
    , (    'https://meta.icos-cp.eu/objects/') AS prefix
    , 'document' AS object_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec' THEN SUBSTRING(obj FROM 41) ELSE NULL END) AS has_object_spec
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum' THEN obj ELSE NULL END) AS has_sha256sum
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes' THEN obj::BIGINT ELSE NULL END) AS has_size_in_bytes
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS was_submitted_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy' THEN SUBSTRING(obj FROM 38) ELSE NULL END) AS was_acquired_by
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNumberOfRows' THEN obj::INTEGER ELSE NULL END) AS has_number_of_rows
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy' THEN SUBSTRING(obj FROM 39) ELSE NULL END) AS was_produced_by
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN SUBSTRING(obj FROM 33) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf') AS is_next_version_of
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualColumnNames' THEN obj ELSE NULL END) AS has_actual_column_names
    , ARRAY_AGG(CASE WHEN pred = 'http://www.w3.org/ns/prov#hadPrimarySource' THEN SUBSTRING(obj FROM 33) ELSE NULL END) FILTER (WHERE pred = 'http://www.w3.org/ns/prov#hadPrimarySource') AS had_primary_source
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable' THEN SUBSTRING(obj FROM 42) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable') AS has_actual_variable
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
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/creator' THEN SUBSTRING(obj FROM 48) ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/creator') AS creator
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DocumentObject'
)
GROUP BY subj
;

-- Populate ct_collections
-- Class: cpmeta:Collection (778 instances)
SELECT 'Populating ct_collections (778 instances)...' AS status;
INSERT OR IGNORE INTO ct_collections (id, rdf_subject, prefix
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
    SUBSTRING(subj FROM 37) AS id
    , subj AS rdf_subject
    , (    'https://meta.icos-cp.eu/collections/') AS prefix
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/hasPart' THEN SUBSTRING(obj FROM 33) ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/hasPart') AS has_part
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/creator' THEN SUBSTRING(obj FROM 48) ELSE NULL END) AS creator
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/title' THEN obj ELSE NULL END) AS title
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , ARRAY_AGG(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN SUBSTRING(obj FROM 37) ELSE NULL END) FILTER (WHERE pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf') AS is_next_version_of
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi' THEN obj ELSE NULL END) AS has_doi
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN SUBSTRING(obj FROM (CASE
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/latlonboxes%' ESCAPE '\' THEN 45
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/position\_%' ESCAPE '\' THEN 43
        WHEN obj LIKE 'http://meta.icos-cp.eu/resources/spcov\_%' ESCAPE '\' THEN 40
    END)) ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Collection'
)
GROUP BY subj;

-- Populate ct_plain_collections
-- Class: cpmeta:PlainCollection (50 instances)
SELECT 'Populating ct_plain_collections (50 instances)...' AS status;
INSERT OR IGNORE INTO ct_plain_collections (id, rdf_subject, prefix
, has_part
, is_next_version_of
)
SELECT
    SUBSTRING(subj FROM 44) AS id
    , subj AS rdf_subject
    , (    'http://meta.icos-cp.eu/resources/nextvcoll_') AS prefix
    , ARRAY_AGG(CASE WHEN pred = 'http://purl.org/dc/terms/hasPart' THEN SUBSTRING(obj FROM 33) ELSE NULL END) FILTER (WHERE pred = 'http://purl.org/dc/terms/hasPart') AS has_part
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN SUBSTRING(obj FROM 33) ELSE NULL END) AS is_next_version_of
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/PlainCollection'
)
GROUP BY subj;
