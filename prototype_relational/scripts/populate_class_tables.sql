-- Generated SQL for class-based tables (POPULATION)
-- Source: class_predicates_analysis.json
-- Triples table: rdf_triples
-- Total tables: 36

-- Run this script after creating tables with create_class_tables.sql

-- ======================================================================
-- POPULATE TABLES
-- ======================================================================

-- Populate ct_object_specs
-- UNION TABLE merging: cpmeta:SimpleObjectSpec, cpmeta:DataObjectSpec
-- Class: MERGED:ct_object_specs (178 instances)
INSERT INTO ct_object_specs (id, spec_type
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
    , 'simple' AS spec_type
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
GROUP BY subj
UNION ALL
SELECT
    subj AS id
    , 'data' AS spec_type
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
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataObjectSpec'
)
GROUP BY subj
;

-- Populate ct_spatial_coverages
-- UNION TABLE merging: cpmeta:SpatialCoverage, cpmeta:LatLonBox, cpmeta:Position
-- Class: MERGED:ct_spatial_coverages (4,466 instances)
INSERT INTO ct_spatial_coverages (id, coverage_type
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
    subj AS id
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
    subj AS id
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
    subj AS id
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

-- Populate ct_organizations
-- UNION TABLE merging: cpmeta:Organization
-- Class: MERGED:ct_organizations (265 instances)
INSERT INTO ct_organizations (id, org_type
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

-- Populate ct_stations
-- UNION TABLE merging: cpmeta:Station, cpmeta:AS, cpmeta:ES, cpmeta:OS, cpmeta:SailDrone, cpmeta:IngosStation, cpmeta:AtmoStation
-- Class: MERGED:ct_stations (628 instances)
INSERT INTO ct_stations (id, station_type
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
, www_w3_org_ns_dcat_theme
, has_atc_id
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
    subj AS id
    , 'station' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
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
    subj AS id
    , 'as' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
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
    subj AS id
    , 'es' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
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
    subj AS id
    , 'os' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
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
    subj AS id
    , 'saildrone' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
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
    subj AS id
    , 'ingos' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
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
    subj AS id
    , 'atmo' AS station_type
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
    , MAX(CASE WHEN pred = 'http://www.w3.org/ns/dcat#theme' THEN obj ELSE NULL END) AS www_w3_org_ns_dcat_theme
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN obj ELSE NULL END) AS has_atc_id
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
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEcosystemType' THEN obj ELSE NULL END) AS has_ecosystem_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualPrecip' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_precip
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualTemp' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_temp
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFunding' THEN obj ELSE NULL END) AS has_funding
    , MAX(CASE WHEN pred = 'http://purl.org/dc/terms/description' THEN obj ELSE NULL END) AS description
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMeanAnnualRadiation' THEN obj::DOUBLE PRECISION ELSE NULL END) AS has_mean_annual_radiation
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedPublication' THEN obj ELSE NULL END) AS has_associated_publication
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isDiscontinued' THEN CASE WHEN LOWER(obj) IN ('true', '1') THEN TRUE WHEN LOWER(obj) IN ('false', '0') THEN FALSE ELSE NULL END ELSE NULL END) AS is_discontinued
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN obj ELSE NULL END) AS see_also
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/AtmoStation'
)
GROUP BY subj
;

-- Populate ct_dataset_specs
-- UNION TABLE merging: cpmeta:DatasetSpec, cpmeta:TabularDatasetSpec
-- Class: MERGED:ct_dataset_specs (80 instances)
INSERT INTO ct_dataset_specs (id, dataset_type
, has_variable
, label
, has_temporal_resolution
, has_column
, comment
)
SELECT
    subj AS id
    , 'dataset' AS dataset_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable' THEN obj ELSE NULL END) AS has_variable
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn' THEN obj ELSE NULL END) AS has_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DatasetSpec'
)
GROUP BY subj
UNION ALL
SELECT
    subj AS id
    , 'tabular' AS dataset_type
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasVariable' THEN obj ELSE NULL END) AS has_variable
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN obj ELSE NULL END) AS label
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN obj ELSE NULL END) AS has_temporal_resolution
    , MAX(CASE WHEN pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasColumn' THEN obj ELSE NULL END) AS has_column
    , MAX(CASE WHEN pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN obj ELSE NULL END) AS comment
FROM rdf_triples
WHERE subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/TabularDatasetSpec'
)
GROUP BY subj
;

-- Populate ct_data_submissions
-- Class: cpmeta:DataSubmission (2,346,277 instances)
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
-- Class: cpmeta:DataObject (2,345,839 instances)
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
-- Class: cpmeta:DataAcquisition (2,343,194 instances)
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
-- Class: cpmeta:DataProduction (1,248,515 instances)
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
-- Class: cpmeta:VariableInfo (4,957 instances)
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
-- Class: cpmeta:Instrument (4,825 instances)
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

-- Populate ct_memberships
-- Class: cpmeta:Membership (1,881 instances)
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
-- Class: cpmeta:Person (1,144 instances)
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
-- Class: cpmeta:Collection (786 instances)
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
-- Class: cpmeta:DocumentObject (438 instances)
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
-- Class: cpmeta:DatasetColumn (420 instances)
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
-- Class: cpmeta:ValueType (307 instances)
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

-- Populate ct_link_boxes
-- Class: cpmeta:LinkBox (184 instances)
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
-- Class: cpmeta:Funding (109 instances)
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
-- Class: cpmeta:Site (99 instances)
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

-- Populate ct_dataset_variables
-- Class: cpmeta:DatasetVariable (76 instances)
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

-- Populate ct_plain_collections
-- Class: cpmeta:PlainCollection (50 instances)
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
-- Class: cpmeta:Funder (45 instances)
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
-- Class: cpmeta:EcosystemType (41 instances)
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
-- Class: cpmeta:WebpageElements (37 instances)
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
-- Class: cpmeta:ClimateZone (35 instances)
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
-- Class: cpmeta:QuantityKind (31 instances)
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
-- Class: cpmeta:ObjectFormat (28 instances)
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

-- Populate ct_projects
-- Class: cpmeta:Project (18 instances)
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
-- Class: cpmeta:ValueFormat (13 instances)
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

-- Populate ct_data_themes
-- Class: cpmeta:DataTheme (9 instances)
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
-- Class: cpmeta:Role (5 instances)
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

-- Populate ct_thematic_centers
-- Class: cpmeta:ThematicCenter (4 instances)
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

-- Populate ct_object_encodings
-- Class: cpmeta:ObjectEncoding (3 instances)
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
-- Class: cpmeta:CentralFacility (2 instances)
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

-- Populate ct_specific_dataset_types
-- Class: cpmeta:SpecificDatasetType (2 instances)
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
