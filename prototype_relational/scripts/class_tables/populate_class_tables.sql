-- Generated SQL for class-based tables (POPULATION)
-- Source: class_predicates_analysis.json
-- Triples table: rdf_triples
-- Total tables: 14

-- Run this script after creating tables with create_class_tables.sql

-- ======================================================================
-- POPULATE TABLES
-- ======================================================================

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
, was_acquired_by_was_performed_with
, was_acquired_by_ended_at_time
, was_acquired_by_started_at_time
, was_acquired_by_was_associated_with
, was_acquired_by_has_sampling_height
, was_acquired_by_has_sampling_point
, was_acquired_by_was_performed_at
, was_produced_by_has_end_time
, was_produced_by_was_performed_by
, was_produced_by_was_hosted_by
, was_produced_by_was_participated_in_by
, was_produced_by_comment
, was_produced_by_see_also
, has_actual_variable_label
, has_actual_variable_has_max_value
, has_actual_variable_has_min_value
)
SELECT
    main.subj AS id
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasName' THEN main.obj ELSE NULL END) AS has_name
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec' THEN main.obj ELSE NULL END) AS has_object_spec
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum' THEN main.obj ELSE NULL END) AS has_sha256sum
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes' THEN main.obj::BIGINT ELSE NULL END) AS has_size_in_bytes
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy' THEN main.obj ELSE NULL END) AS was_submitted_by
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy' THEN main.obj ELSE NULL END) AS was_acquired_by
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasNumberOfRows' THEN main.obj::INTEGER ELSE NULL END) AS has_number_of_rows
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy' THEN main.obj ELSE NULL END) AS was_produced_by
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf' THEN main.obj ELSE NULL END) AS is_next_version_of
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualColumnNames' THEN main.obj ELSE NULL END) AS has_actual_column_names
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/ns/prov#hadPrimarySource' THEN main.obj ELSE NULL END) AS had_primary_source
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSpatialCoverage' THEN main.obj ELSE NULL END) AS has_spatial_coverage
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable' THEN main.obj ELSE NULL END) AS has_actual_variable
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi' THEN main.obj ELSE NULL END) AS has_doi
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords' THEN main.obj ELSE NULL END) AS has_keywords
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/CONTACT%20POINT' THEN main.obj ELSE NULL END) AS contact_20_point
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/CONTRIBUTOR' THEN main.obj ELSE NULL END) AS contributor
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/MEASUREMENT%20METHOD' THEN main.obj ELSE NULL END) AS measurement_20_method
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/MEASUREMENT%20SCALE' THEN main.obj ELSE NULL END) AS measurement_20_scale
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/MEASUREMENT%20UNIT' THEN main.obj ELSE NULL END) AS measurement_20_unit
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/OBSERVATION%20CATEGORY' THEN main.obj ELSE NULL END) AS observation_20_category
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/PARAMETER' THEN main.obj ELSE NULL END) AS parameter
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/SAMPLING%20TYPE' THEN main.obj ELSE NULL END) AS sampling_20_type
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/resources/wdcgg/TIME%20INTERVAL' THEN main.obj ELSE NULL END) AS time_20_interval
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN main.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_end_time
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartTime' THEN main.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_start_time
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasTemporalResolution' THEN main.obj ELSE NULL END) AS has_temporal_resolution
    , MAX(CASE WHEN main.pred = 'http://purl.org/dc/terms/description' THEN main.obj ELSE NULL END) AS description
    , MAX(CASE WHEN main.pred = 'http://purl.org/dc/terms/title' THEN main.obj ELSE NULL END) AS title
    , MAX(CASE WHEN main.pred = 'http://purl.org/dc/terms/license' THEN main.obj ELSE NULL END) AS license
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN main.obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN inl_was_acquired_by_was_performed_with.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith' THEN inl_was_acquired_by_was_performed_with.obj ELSE NULL END) AS was_acquired_by_was_performed_with
    , MAX(CASE WHEN inl_was_acquired_by_ended_at_time.pred = 'http://www.w3.org/ns/prov#endedAtTime' THEN inl_was_acquired_by_ended_at_time.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS was_acquired_by_ended_at_time
    , MAX(CASE WHEN inl_was_acquired_by_started_at_time.pred = 'http://www.w3.org/ns/prov#startedAtTime' THEN inl_was_acquired_by_started_at_time.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS was_acquired_by_started_at_time
    , MAX(CASE WHEN inl_was_acquired_by_was_associated_with.pred = 'http://www.w3.org/ns/prov#wasAssociatedWith' THEN inl_was_acquired_by_was_associated_with.obj ELSE NULL END) AS was_acquired_by_was_associated_with
    , MAX(CASE WHEN inl_was_acquired_by_has_sampling_height.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingHeight' THEN inl_was_acquired_by_has_sampling_height.obj::DOUBLE PRECISION ELSE NULL END) AS was_acquired_by_has_sampling_height
    , MAX(CASE WHEN inl_was_acquired_by_has_sampling_point.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingPoint' THEN inl_was_acquired_by_has_sampling_point.obj ELSE NULL END) AS was_acquired_by_has_sampling_point
    , MAX(CASE WHEN inl_was_acquired_by_was_performed_at.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedAt' THEN inl_was_acquired_by_was_performed_at.obj ELSE NULL END) AS was_acquired_by_was_performed_at
    , MAX(CASE WHEN inl_was_produced_by_has_end_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN inl_was_produced_by_has_end_time.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS was_produced_by_has_end_time
    , MAX(CASE WHEN inl_was_produced_by_was_performed_by.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedBy' THEN inl_was_produced_by_was_performed_by.obj ELSE NULL END) AS was_produced_by_was_performed_by
    , MAX(CASE WHEN inl_was_produced_by_was_hosted_by.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasHostedBy' THEN inl_was_produced_by_was_hosted_by.obj ELSE NULL END) AS was_produced_by_was_hosted_by
    , MAX(CASE WHEN inl_was_produced_by_was_participated_in_by.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasParticipatedInBy' THEN inl_was_produced_by_was_participated_in_by.obj ELSE NULL END) AS was_produced_by_was_participated_in_by
    , MAX(CASE WHEN inl_was_produced_by_comment.pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN inl_was_produced_by_comment.obj ELSE NULL END) AS was_produced_by_comment
    , MAX(CASE WHEN inl_was_produced_by_see_also.pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN inl_was_produced_by_see_also.obj ELSE NULL END) AS was_produced_by_see_also
    , MAX(CASE WHEN inl_has_actual_variable_label.pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN inl_has_actual_variable_label.obj ELSE NULL END) AS has_actual_variable_label
    , MAX(CASE WHEN inl_has_actual_variable_has_max_value.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMaxValue' THEN inl_has_actual_variable_has_max_value.obj::DOUBLE PRECISION ELSE NULL END) AS has_actual_variable_has_max_value
    , MAX(CASE WHEN inl_has_actual_variable_has_min_value.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMinValue' THEN inl_has_actual_variable_has_min_value.obj::DOUBLE PRECISION ELSE NULL END) AS has_actual_variable_has_min_value
FROM rdf_triples main
LEFT JOIN rdf_triples fk_inl_was_acquired_by_was_performed_with ON main.subj = fk_inl_was_acquired_by_was_performed_with.subj AND fk_inl_was_acquired_by_was_performed_with.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_was_performed_with ON fk_inl_was_acquired_by_was_performed_with.obj = inl_was_acquired_by_was_performed_with.subj
LEFT JOIN rdf_triples fk_inl_was_acquired_by_ended_at_time ON main.subj = fk_inl_was_acquired_by_ended_at_time.subj AND fk_inl_was_acquired_by_ended_at_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_ended_at_time ON fk_inl_was_acquired_by_ended_at_time.obj = inl_was_acquired_by_ended_at_time.subj
LEFT JOIN rdf_triples fk_inl_was_acquired_by_started_at_time ON main.subj = fk_inl_was_acquired_by_started_at_time.subj AND fk_inl_was_acquired_by_started_at_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_started_at_time ON fk_inl_was_acquired_by_started_at_time.obj = inl_was_acquired_by_started_at_time.subj
LEFT JOIN rdf_triples fk_inl_was_acquired_by_was_associated_with ON main.subj = fk_inl_was_acquired_by_was_associated_with.subj AND fk_inl_was_acquired_by_was_associated_with.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_was_associated_with ON fk_inl_was_acquired_by_was_associated_with.obj = inl_was_acquired_by_was_associated_with.subj
LEFT JOIN rdf_triples fk_inl_was_acquired_by_has_sampling_height ON main.subj = fk_inl_was_acquired_by_has_sampling_height.subj AND fk_inl_was_acquired_by_has_sampling_height.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_has_sampling_height ON fk_inl_was_acquired_by_has_sampling_height.obj = inl_was_acquired_by_has_sampling_height.subj
LEFT JOIN rdf_triples fk_inl_was_acquired_by_has_sampling_point ON main.subj = fk_inl_was_acquired_by_has_sampling_point.subj AND fk_inl_was_acquired_by_has_sampling_point.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_has_sampling_point ON fk_inl_was_acquired_by_has_sampling_point.obj = inl_was_acquired_by_has_sampling_point.subj
LEFT JOIN rdf_triples fk_inl_was_acquired_by_was_performed_at ON main.subj = fk_inl_was_acquired_by_was_performed_at.subj AND fk_inl_was_acquired_by_was_performed_at.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy'
LEFT JOIN rdf_triples inl_was_acquired_by_was_performed_at ON fk_inl_was_acquired_by_was_performed_at.obj = inl_was_acquired_by_was_performed_at.subj
LEFT JOIN rdf_triples fk_inl_was_produced_by_has_end_time ON main.subj = fk_inl_was_produced_by_has_end_time.subj AND fk_inl_was_produced_by_has_end_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy'
LEFT JOIN rdf_triples inl_was_produced_by_has_end_time ON fk_inl_was_produced_by_has_end_time.obj = inl_was_produced_by_has_end_time.subj
LEFT JOIN rdf_triples fk_inl_was_produced_by_was_performed_by ON main.subj = fk_inl_was_produced_by_was_performed_by.subj AND fk_inl_was_produced_by_was_performed_by.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy'
LEFT JOIN rdf_triples inl_was_produced_by_was_performed_by ON fk_inl_was_produced_by_was_performed_by.obj = inl_was_produced_by_was_performed_by.subj
LEFT JOIN rdf_triples fk_inl_was_produced_by_was_hosted_by ON main.subj = fk_inl_was_produced_by_was_hosted_by.subj AND fk_inl_was_produced_by_was_hosted_by.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy'
LEFT JOIN rdf_triples inl_was_produced_by_was_hosted_by ON fk_inl_was_produced_by_was_hosted_by.obj = inl_was_produced_by_was_hosted_by.subj
LEFT JOIN rdf_triples fk_inl_was_produced_by_was_participated_in_by ON main.subj = fk_inl_was_produced_by_was_participated_in_by.subj AND fk_inl_was_produced_by_was_participated_in_by.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy'
LEFT JOIN rdf_triples inl_was_produced_by_was_participated_in_by ON fk_inl_was_produced_by_was_participated_in_by.obj = inl_was_produced_by_was_participated_in_by.subj
LEFT JOIN rdf_triples fk_inl_was_produced_by_comment ON main.subj = fk_inl_was_produced_by_comment.subj AND fk_inl_was_produced_by_comment.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy'
LEFT JOIN rdf_triples inl_was_produced_by_comment ON fk_inl_was_produced_by_comment.obj = inl_was_produced_by_comment.subj
LEFT JOIN rdf_triples fk_inl_was_produced_by_see_also ON main.subj = fk_inl_was_produced_by_see_also.subj AND fk_inl_was_produced_by_see_also.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy'
LEFT JOIN rdf_triples inl_was_produced_by_see_also ON fk_inl_was_produced_by_see_also.obj = inl_was_produced_by_see_also.subj
LEFT JOIN rdf_triples fk_inl_has_actual_variable_label ON main.subj = fk_inl_has_actual_variable_label.subj AND fk_inl_has_actual_variable_label.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable'
LEFT JOIN rdf_triples inl_has_actual_variable_label ON fk_inl_has_actual_variable_label.obj = inl_has_actual_variable_label.subj
LEFT JOIN rdf_triples fk_inl_has_actual_variable_has_max_value ON main.subj = fk_inl_has_actual_variable_has_max_value.subj AND fk_inl_has_actual_variable_has_max_value.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable'
LEFT JOIN rdf_triples inl_has_actual_variable_has_max_value ON fk_inl_has_actual_variable_has_max_value.obj = inl_has_actual_variable_has_max_value.subj
LEFT JOIN rdf_triples fk_inl_has_actual_variable_has_min_value ON main.subj = fk_inl_has_actual_variable_has_min_value.subj AND fk_inl_has_actual_variable_has_min_value.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualVariable'
LEFT JOIN rdf_triples inl_has_actual_variable_has_min_value ON fk_inl_has_actual_variable_has_min_value.obj = inl_has_actual_variable_has_min_value.subj
WHERE main.subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/DataObject'
)
GROUP BY main.subj;

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
, has_membership_label
, has_membership_has_role
, has_membership_at_organization
, has_membership_has_start_time
, has_membership_has_attribution_weight
, has_membership_has_end_time
, has_membership_has_extra_role_info
)
SELECT
    main.subj AS id
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership' THEN main.obj ELSE NULL END) AS has_membership
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasFirstName' THEN main.obj ELSE NULL END) AS has_first_name
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasLastName' THEN main.obj ELSE NULL END) AS has_last_name
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEmail' THEN main.obj ELSE NULL END) AS has_email
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEtcId' THEN main.obj ELSE NULL END) AS has_etc_id
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOrcidId' THEN main.obj ELSE NULL END) AS has_orcid_id
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAtcId' THEN main.obj ELSE NULL END) AS has_atc_id
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasOtcId' THEN main.obj ELSE NULL END) AS has_otc_id
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN main.obj ELSE NULL END) AS label
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN main.obj ELSE NULL END) AS comment
    , MAX(CASE WHEN inl_has_membership_label.pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN inl_has_membership_label.obj ELSE NULL END) AS has_membership_label
    , MAX(CASE WHEN inl_has_membership_has_role.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasRole' THEN inl_has_membership_has_role.obj ELSE NULL END) AS has_membership_has_role
    , MAX(CASE WHEN inl_has_membership_at_organization.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/atOrganization' THEN inl_has_membership_at_organization.obj ELSE NULL END) AS has_membership_at_organization
    , MAX(CASE WHEN inl_has_membership_has_start_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartTime' THEN inl_has_membership_has_start_time.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_membership_has_start_time
    , MAX(CASE WHEN inl_has_membership_has_attribution_weight.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAttributionWeight' THEN inl_has_membership_has_attribution_weight.obj::SMALLINT ELSE NULL END) AS has_membership_has_attribution_weight
    , MAX(CASE WHEN inl_has_membership_has_end_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime' THEN inl_has_membership_has_end_time.obj::TIMESTAMP WITH TIME ZONE ELSE NULL END) AS has_membership_has_end_time
    , MAX(CASE WHEN inl_has_membership_has_extra_role_info.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasExtraRoleInfo' THEN inl_has_membership_has_extra_role_info.obj ELSE NULL END) AS has_membership_has_extra_role_info
FROM rdf_triples main
LEFT JOIN rdf_triples fk_inl_has_membership_label ON main.subj = fk_inl_has_membership_label.subj AND fk_inl_has_membership_label.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_label ON fk_inl_has_membership_label.obj = inl_has_membership_label.subj
LEFT JOIN rdf_triples fk_inl_has_membership_has_role ON main.subj = fk_inl_has_membership_has_role.subj AND fk_inl_has_membership_has_role.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_has_role ON fk_inl_has_membership_has_role.obj = inl_has_membership_has_role.subj
LEFT JOIN rdf_triples fk_inl_has_membership_at_organization ON main.subj = fk_inl_has_membership_at_organization.subj AND fk_inl_has_membership_at_organization.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_at_organization ON fk_inl_has_membership_at_organization.obj = inl_has_membership_at_organization.subj
LEFT JOIN rdf_triples fk_inl_has_membership_has_start_time ON main.subj = fk_inl_has_membership_has_start_time.subj AND fk_inl_has_membership_has_start_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_has_start_time ON fk_inl_has_membership_has_start_time.obj = inl_has_membership_has_start_time.subj
LEFT JOIN rdf_triples fk_inl_has_membership_has_attribution_weight ON main.subj = fk_inl_has_membership_has_attribution_weight.subj AND fk_inl_has_membership_has_attribution_weight.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_has_attribution_weight ON fk_inl_has_membership_has_attribution_weight.obj = inl_has_membership_has_attribution_weight.subj
LEFT JOIN rdf_triples fk_inl_has_membership_has_end_time ON main.subj = fk_inl_has_membership_has_end_time.subj AND fk_inl_has_membership_has_end_time.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_has_end_time ON fk_inl_has_membership_has_end_time.obj = inl_has_membership_has_end_time.subj
LEFT JOIN rdf_triples fk_inl_has_membership_has_extra_role_info ON main.subj = fk_inl_has_membership_has_extra_role_info.subj AND fk_inl_has_membership_has_extra_role_info.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasMembership'
LEFT JOIN rdf_triples inl_has_membership_has_extra_role_info ON fk_inl_has_membership_has_extra_role_info.obj = inl_has_membership_has_extra_role_info.subj
WHERE main.subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/Person'
)
GROUP BY main.subj;

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

-- Populate ct_value_types
-- Class: cpmeta:ValueType (307 instances)
INSERT INTO ct_value_types (id
, label
, has_quantity_kind
, has_unit
, comment
, www_w3_org_2004_02_skos_core_exact_match
, see_also
, has_quantity_kind_label
, has_quantity_kind_comment
)
SELECT
    main.subj AS id
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN main.obj ELSE NULL END) AS label
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasQuantityKind' THEN main.obj ELSE NULL END) AS has_quantity_kind
    , MAX(CASE WHEN main.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasUnit' THEN main.obj ELSE NULL END) AS has_unit
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN main.obj ELSE NULL END) AS comment
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2004/02/skos/core#exactMatch' THEN main.obj ELSE NULL END) AS www_w3_org_2004_02_skos_core_exact_match
    , MAX(CASE WHEN main.pred = 'http://www.w3.org/2000/01/rdf-schema#seeAlso' THEN main.obj ELSE NULL END) AS see_also
    , MAX(CASE WHEN inl_has_quantity_kind_label.pred = 'http://www.w3.org/2000/01/rdf-schema#label' THEN inl_has_quantity_kind_label.obj ELSE NULL END) AS has_quantity_kind_label
    , MAX(CASE WHEN inl_has_quantity_kind_comment.pred = 'http://www.w3.org/2000/01/rdf-schema#comment' THEN inl_has_quantity_kind_comment.obj ELSE NULL END) AS has_quantity_kind_comment
FROM rdf_triples main
LEFT JOIN rdf_triples fk_inl_has_quantity_kind_label ON main.subj = fk_inl_has_quantity_kind_label.subj AND fk_inl_has_quantity_kind_label.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasQuantityKind'
LEFT JOIN rdf_triples inl_has_quantity_kind_label ON fk_inl_has_quantity_kind_label.obj = inl_has_quantity_kind_label.subj
LEFT JOIN rdf_triples fk_inl_has_quantity_kind_comment ON main.subj = fk_inl_has_quantity_kind_comment.subj AND fk_inl_has_quantity_kind_comment.pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasQuantityKind'
LEFT JOIN rdf_triples inl_has_quantity_kind_comment ON fk_inl_has_quantity_kind_comment.obj = inl_has_quantity_kind_comment.subj
WHERE main.subj IN (
    SELECT subj FROM rdf_triples
    WHERE pred = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type' AND obj = 'http://meta.icos-cp.eu/ontologies/cpmeta/ValueType'
)
GROUP BY main.subj;

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
