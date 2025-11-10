-- Generated SQL for prefix-based tables
-- Each table contains subjects with a specific prefix
-- Structure: (id, predicate, object)
--   id: subject URI with prefix removed
--   predicate: full predicate URI
--   object: object value (URI or literal)

-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/files/
-- Table: meta_icos_cp_eu_files
-- ======================================================================

DROP TABLE IF EXISTS meta_icos_cp_eu_files CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS meta_icos_cp_eu_files (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO meta_icos_cp_eu_files (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 30) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/files/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_meta_icos_cp_eu_files_id ON meta_icos_cp_eu_files(id);
CREATE INDEX IF NOT EXISTS idx_meta_icos_cp_eu_files_predicate ON meta_icos_cp_eu_files(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/cpmeta/
-- Table: ontologies_cpmeta
-- ======================================================================

DROP TABLE IF EXISTS ontologies_cpmeta CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS ontologies_cpmeta (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO ontologies_cpmeta (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 42) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/cpmeta/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_ontologies_cpmeta_id ON ontologies_cpmeta(id);
CREATE INDEX IF NOT EXISTS idx_ontologies_cpmeta_predicate ON ontologies_cpmeta(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/cpmeta/bnode_
-- Table: cpmeta_bnode
-- ======================================================================

DROP TABLE IF EXISTS cpmeta_bnode CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS cpmeta_bnode (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO cpmeta_bnode (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 48) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/cpmeta/bnode_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_cpmeta_bnode_id ON cpmeta_bnode(id);
CREATE INDEX IF NOT EXISTS idx_cpmeta_bnode_predicate ON cpmeta_bnode(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/otcmeta/
-- Table: ontologies_otcmeta
-- ======================================================================

DROP TABLE IF EXISTS ontologies_otcmeta CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS ontologies_otcmeta (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO ontologies_otcmeta (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 43) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/otcmeta/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_ontologies_otcmeta_id ON ontologies_otcmeta(id);
CREATE INDEX IF NOT EXISTS idx_ontologies_otcmeta_predicate ON ontologies_otcmeta(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/otcmeta/bnode_
-- Table: otcmeta_bnode
-- ======================================================================

DROP TABLE IF EXISTS otcmeta_bnode CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS otcmeta_bnode (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO otcmeta_bnode (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 49) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/otcmeta/bnode_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_otcmeta_bnode_id ON otcmeta_bnode(id);
CREATE INDEX IF NOT EXISTS idx_otcmeta_bnode_predicate ON otcmeta_bnode(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/stationentry/
-- Table: ontologies_stationentry
-- ======================================================================

DROP TABLE IF EXISTS ontologies_stationentry CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS ontologies_stationentry (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO ontologies_stationentry (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 48) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/stationentry/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_ontologies_stationentry_id ON ontologies_stationentry(id);
CREATE INDEX IF NOT EXISTS idx_ontologies_stationentry_predicate ON ontologies_stationentry(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/stationentry/ES/
-- Table: stationentry_es
-- ======================================================================

DROP TABLE IF EXISTS stationentry_es CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS stationentry_es (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO stationentry_es (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 51) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/stationentry/ES/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_stationentry_es_id ON stationentry_es(id);
CREATE INDEX IF NOT EXISTS idx_stationentry_es_predicate ON stationentry_es(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/stationentry/PI/
-- Table: stationentry_pi
-- ======================================================================

DROP TABLE IF EXISTS stationentry_pi CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS stationentry_pi (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO stationentry_pi (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 51) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/stationentry/PI/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_stationentry_pi_id ON stationentry_pi(id);
CREATE INDEX IF NOT EXISTS idx_stationentry_pi_predicate ON stationentry_pi(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/ontologies/stationentry/bnode_
-- Table: stationentry_bnode
-- ======================================================================

DROP TABLE IF EXISTS stationentry_bnode CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS stationentry_bnode (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO stationentry_bnode (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 54) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/ontologies/stationentry/bnode_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_stationentry_bnode_id ON stationentry_bnode(id);
CREATE INDEX IF NOT EXISTS idx_stationentry_bnode_predicate ON stationentry_bnode(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/acq_
-- Table: resources_acq
-- ======================================================================

DROP TABLE IF EXISTS resources_acq CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_acq (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_acq (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 38) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/acq_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_acq_id ON resources_acq(id);
CREATE INDEX IF NOT EXISTS idx_resources_acq_predicate ON resources_acq(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/cpmeta/
-- Table: resources_cpmeta
-- ======================================================================

DROP TABLE IF EXISTS resources_cpmeta CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_cpmeta (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_cpmeta (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 41) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/cpmeta/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_cpmeta_id ON resources_cpmeta(id);
CREATE INDEX IF NOT EXISTS idx_resources_cpmeta_predicate ON resources_cpmeta(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/dcat/
-- Table: resources_dcat
-- ======================================================================

DROP TABLE IF EXISTS resources_dcat CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_dcat (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_dcat (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 39) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/dcat/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_dcat_id ON resources_dcat(id);
CREATE INDEX IF NOT EXISTS idx_resources_dcat_predicate ON resources_dcat(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/dcat/bnode_
-- Table: dcat_bnode
-- ======================================================================

DROP TABLE IF EXISTS dcat_bnode CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS dcat_bnode (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO dcat_bnode (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 45) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/dcat/bnode_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_dcat_bnode_id ON dcat_bnode(id);
CREATE INDEX IF NOT EXISTS idx_dcat_bnode_predicate ON dcat_bnode(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/dcat/var/
-- Table: dcat_var
-- ======================================================================

DROP TABLE IF EXISTS dcat_var CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS dcat_var (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO dcat_var (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 43) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/dcat/var/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_dcat_var_id ON dcat_var(id);
CREATE INDEX IF NOT EXISTS idx_dcat_var_predicate ON dcat_var(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/deployments/
-- Table: resources_deployments
-- ======================================================================

DROP TABLE IF EXISTS resources_deployments CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_deployments (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_deployments (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/deployments/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_deployments_id ON resources_deployments(id);
CREATE INDEX IF NOT EXISTS idx_resources_deployments_predicate ON resources_deployments(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/fundings/
-- Table: resources_fundings
-- ======================================================================

DROP TABLE IF EXISTS resources_fundings CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_fundings (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_fundings (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 43) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/fundings/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_fundings_id ON resources_fundings(id);
CREATE INDEX IF NOT EXISTS idx_resources_fundings_predicate ON resources_fundings(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/icos/
-- Table: resources_icos
-- ======================================================================

DROP TABLE IF EXISTS resources_icos CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_icos (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_icos (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 39) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/icos/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_icos_id ON resources_icos(id);
CREATE INDEX IF NOT EXISTS idx_resources_icos_predicate ON resources_icos(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/instruments/ATC_
-- Table: instruments_atc
-- ======================================================================

DROP TABLE IF EXISTS instruments_atc CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS instruments_atc (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO instruments_atc (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 50) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/instruments/ATC_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_instruments_atc_id ON instruments_atc(id);
CREATE INDEX IF NOT EXISTS idx_instruments_atc_predicate ON instruments_atc(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/instruments/ETC_
-- Table: instruments_etc
-- ======================================================================

DROP TABLE IF EXISTS instruments_etc CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS instruments_etc (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO instruments_etc (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 50) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/instruments/ETC_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_instruments_etc_id ON instruments_etc(id);
CREATE INDEX IF NOT EXISTS idx_instruments_etc_predicate ON instruments_etc(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/instruments/OTC_
-- Table: instruments_otc
-- ======================================================================

DROP TABLE IF EXISTS instruments_otc CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS instruments_otc (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO instruments_otc (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 50) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/instruments/OTC_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_instruments_otc_id ON instruments_otc(id);
CREATE INDEX IF NOT EXISTS idx_instruments_otc_predicate ON instruments_otc(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/memberships/
-- Table: resources_memberships
-- ======================================================================

DROP TABLE IF EXISTS resources_memberships CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_memberships (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_memberships (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/memberships/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_memberships_id ON resources_memberships(id);
CREATE INDEX IF NOT EXISTS idx_resources_memberships_predicate ON resources_memberships(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/nextvcoll_
-- Table: resources_nextvcoll
-- ======================================================================

DROP TABLE IF EXISTS resources_nextvcoll CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_nextvcoll (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_nextvcoll (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 44) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/nextvcoll_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_nextvcoll_id ON resources_nextvcoll(id);
CREATE INDEX IF NOT EXISTS idx_resources_nextvcoll_predicate ON resources_nextvcoll(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/organizations/
-- Table: resources_organizations
-- ======================================================================

DROP TABLE IF EXISTS resources_organizations CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_organizations (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_organizations (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 48) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/organizations/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_organizations_id ON resources_organizations(id);
CREATE INDEX IF NOT EXISTS idx_resources_organizations_predicate ON resources_organizations(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/organizations/etcorg_
-- Table: organizations_etcorg
-- ======================================================================

DROP TABLE IF EXISTS organizations_etcorg CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS organizations_etcorg (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO organizations_etcorg (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 55) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/organizations/etcorg_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_organizations_etcorg_id ON organizations_etcorg(id);
CREATE INDEX IF NOT EXISTS idx_organizations_etcorg_predicate ON organizations_etcorg(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/otcmeta/
-- Table: resources_otcmeta
-- ======================================================================

DROP TABLE IF EXISTS resources_otcmeta CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_otcmeta (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_otcmeta (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 42) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/otcmeta/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_otcmeta_id ON resources_otcmeta(id);
CREATE INDEX IF NOT EXISTS idx_resources_otcmeta_predicate ON resources_otcmeta(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/people/
-- Table: resources_people
-- ======================================================================

DROP TABLE IF EXISTS resources_people CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_people (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_people (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 41) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/people/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_people_id ON resources_people(id);
CREATE INDEX IF NOT EXISTS idx_resources_people_predicate ON resources_people(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/prod_
-- Table: resources_prod
-- ======================================================================

DROP TABLE IF EXISTS resources_prod CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_prod (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_prod (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 39) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/prod_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_prod_id ON resources_prod(id);
CREATE INDEX IF NOT EXISTS idx_resources_prod_predicate ON resources_prod(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/prod_contribs_
-- Table: resources_prod_contribs
-- ======================================================================

DROP TABLE IF EXISTS resources_prod_contribs CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_prod_contribs (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_prod_contribs (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 48) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/prod_contribs_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_prod_contribs_id ON resources_prod_contribs(id);
CREATE INDEX IF NOT EXISTS idx_resources_prod_contribs_predicate ON resources_prod_contribs(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/spcov_
-- Table: resources_spcov
-- ======================================================================

DROP TABLE IF EXISTS resources_spcov CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_spcov (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_spcov (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 40) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/spcov_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_spcov_id ON resources_spcov(id);
CREATE INDEX IF NOT EXISTS idx_resources_spcov_predicate ON resources_spcov(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/stationentry/
-- Table: resources_stationentry
-- ======================================================================

DROP TABLE IF EXISTS resources_stationentry CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_stationentry (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_stationentry (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 47) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/stationentry/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_stationentry_id ON resources_stationentry(id);
CREATE INDEX IF NOT EXISTS idx_resources_stationentry_predicate ON resources_stationentry(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/stations/
-- Table: resources_stations
-- ======================================================================

DROP TABLE IF EXISTS resources_stations CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_stations (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_stations (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 43) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/stations/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_stations_id ON resources_stations(id);
CREATE INDEX IF NOT EXISTS idx_resources_stations_predicate ON resources_stations(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/stations/AS_
-- Table: stations_as
-- ======================================================================

DROP TABLE IF EXISTS stations_as CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS stations_as (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO stations_as (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/stations/AS_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_stations_as_id ON stations_as(id);
CREATE INDEX IF NOT EXISTS idx_stations_as_predicate ON stations_as(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/stations/ES_
-- Table: stations_es
-- ======================================================================

DROP TABLE IF EXISTS stations_es CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS stations_es (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO stations_es (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/stations/ES_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_stations_es_id ON stations_es(id);
CREATE INDEX IF NOT EXISTS idx_stations_es_predicate ON stations_es(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/subm_
-- Table: resources_subm
-- ======================================================================

DROP TABLE IF EXISTS resources_subm CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_subm (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_subm (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 39) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/subm_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_subm_id ON resources_subm(id);
CREATE INDEX IF NOT EXISTS idx_resources_subm_predicate ON resources_subm(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_A_Public_power_
-- Table: resources_varinfo_a_public_power
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_a_public_power CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_a_public_power (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_a_public_power (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 57) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_A_Public_power_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_a_public_power_id ON resources_varinfo_a_public_power(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_a_public_power_predicate ON resources_varinfo_a_public_power(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_B_Industry_
-- Table: resources_varinfo_b_industry
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_b_industry CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_b_industry (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_b_industry (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 53) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_B_Industry_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_b_industry_id ON resources_varinfo_b_industry(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_b_industry_predicate ON resources_varinfo_b_industry(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_C_Other_stationary_combustion_consumer_
-- Table: resources_varinfo_c_other_stationary_combustion_consumer
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_c_other_stationary_combustion_consumer CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_c_other_stationary_combustion_consumer (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_c_other_stationary_combustion_consumer (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 81) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_C_Other_stationary_combustion_consumer_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_c_other_stationary_combustion_consumer_id ON resources_varinfo_c_other_stationary_combustion_consumer(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_c_other_stationary_combustion_consumer_predicate ON resources_varinfo_c_other_stationary_combustion_consumer(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_ET_
-- Table: resources_varinfo_et
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_et CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_et (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_et (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 45) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_ET_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_et_id ON resources_varinfo_et(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_et_predicate ON resources_varinfo_et(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_ET_T_
-- Table: resources_varinfo_et_t
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_et_t CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_et_t (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_et_t (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 47) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_ET_T_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_et_t_id ON resources_varinfo_et_t(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_et_t_predicate ON resources_varinfo_et_t(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_F_On-road_
-- Table: resources_varinfo_f_on_road
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_f_on_road CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_f_on_road (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_f_on_road (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 52) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_F_On-road_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_f_on_road_id ON resources_varinfo_f_on_road(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_f_on_road_predicate ON resources_varinfo_f_on_road(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_GPP_
-- Table: resources_varinfo_gpp
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_gpp CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_gpp (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_gpp (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_GPP_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_gpp_id ON resources_varinfo_gpp(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_gpp_predicate ON resources_varinfo_gpp(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_G_Shipping_
-- Table: resources_varinfo_g_shipping
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_g_shipping CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_g_shipping (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_g_shipping (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 53) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_G_Shipping_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_g_shipping_id ON resources_varinfo_g_shipping(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_g_shipping_predicate ON resources_varinfo_g_shipping(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_H_Aviation_
-- Table: resources_varinfo_h_aviation
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_h_aviation CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_h_aviation (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_h_aviation (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 53) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_H_Aviation_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_h_aviation_id ON resources_varinfo_h_aviation(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_h_aviation_predicate ON resources_varinfo_h_aviation(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_I_Off-road_
-- Table: resources_varinfo_i_off_road
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_i_off_road CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_i_off_road (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_i_off_road (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 53) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_I_Off-road_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_i_off_road_id ON resources_varinfo_i_off_road(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_i_off_road_predicate ON resources_varinfo_i_off_road(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_NEE_
-- Table: resources_varinfo_nee
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_nee CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_nee (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_nee (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_NEE_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_nee_id ON resources_varinfo_nee(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_nee_predicate ON resources_varinfo_nee(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_anthropogenic_
-- Table: resources_varinfo_anthropogenic
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_anthropogenic CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_anthropogenic (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_anthropogenic (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 56) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_anthropogenic_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_anthropogenic_id ON resources_varinfo_anthropogenic(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_anthropogenic_predicate ON resources_varinfo_anthropogenic(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_cement_
-- Table: resources_varinfo_cement
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_cement CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_cement (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_cement (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 49) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_cement_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_cement_id ON resources_varinfo_cement(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_cement_predicate ON resources_varinfo_cement(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_combustion_
-- Table: resources_varinfo_combustion
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_combustion CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_combustion (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_combustion (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 53) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_combustion_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_combustion_id ON resources_varinfo_combustion(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_combustion_predicate ON resources_varinfo_combustion(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_emission_
-- Table: resources_varinfo_emission
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_emission CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_emission (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_emission (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 51) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_emission_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_emission_id ON resources_varinfo_emission(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_emission_predicate ON resources_varinfo_emission(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_fire_
-- Table: resources_varinfo_fire
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_fire CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_fire (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_fire (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 47) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_fire_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_fire_id ON resources_varinfo_fire(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_fire_predicate ON resources_varinfo_fire(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_foot_
-- Table: resources_varinfo_foot
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_foot CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_foot (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_foot (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 47) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_foot_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_foot_id ON resources_varinfo_foot(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_foot_predicate ON resources_varinfo_foot(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_gpp_
-- Table: resources_varinfo_gpp2
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_gpp2 CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_gpp2 (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_gpp2 (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_gpp_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_gpp2_id ON resources_varinfo_gpp2(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_gpp2_predicate ON resources_varinfo_gpp2(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_nep_
-- Table: resources_varinfo_nep
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_nep CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_nep (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_nep (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_nep_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_nep_id ON resources_varinfo_nep(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_nep_predicate ON resources_varinfo_nep(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/varinfo_ocean_
-- Table: resources_varinfo_ocean
-- ======================================================================

DROP TABLE IF EXISTS resources_varinfo_ocean CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_varinfo_ocean (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_varinfo_ocean (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 48) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/varinfo_ocean_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_ocean_id ON resources_varinfo_ocean(id);
CREATE INDEX IF NOT EXISTS idx_resources_varinfo_ocean_predicate ON resources_varinfo_ocean(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/wdcgg/
-- Table: resources_wdcgg
-- ======================================================================

DROP TABLE IF EXISTS resources_wdcgg CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_wdcgg (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_wdcgg (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 40) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_wdcgg_id ON resources_wdcgg(id);
CREATE INDEX IF NOT EXISTS idx_resources_wdcgg_predicate ON resources_wdcgg(predicate);


-- ======================================================================
-- Prefix: http://meta.icos-cp.eu/resources/wdcgg/station/
-- Table: wdcgg_station
-- ======================================================================

DROP TABLE IF EXISTS wdcgg_station CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS wdcgg_station (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO wdcgg_station (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 48) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'http://meta.icos-cp.eu/resources/wdcgg/station/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_wdcgg_station_id ON wdcgg_station(id);
CREATE INDEX IF NOT EXISTS idx_wdcgg_station_predicate ON wdcgg_station(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/objects/
-- Table: meta_fieldsites_se_objects
-- ======================================================================

DROP TABLE IF EXISTS meta_fieldsites_se_objects CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS meta_fieldsites_se_objects (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO meta_fieldsites_se_objects (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 36) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/objects/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_meta_fieldsites_se_objects_id ON meta_fieldsites_se_objects(id);
CREATE INDEX IF NOT EXISTS idx_meta_fieldsites_se_objects_predicate ON meta_fieldsites_se_objects(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/
-- Table: meta_fieldsites_se_resources
-- ======================================================================

DROP TABLE IF EXISTS meta_fieldsites_se_resources CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS meta_fieldsites_se_resources (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO meta_fieldsites_se_resources (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 38) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_meta_fieldsites_se_resources_id ON meta_fieldsites_se_resources(id);
CREATE INDEX IF NOT EXISTS idx_meta_fieldsites_se_resources_predicate ON meta_fieldsites_se_resources(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/acq_
-- Table: resources_acq2
-- ======================================================================

DROP TABLE IF EXISTS resources_acq2 CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_acq2 (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_acq2 (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 42) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/acq_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_acq2_id ON resources_acq2(id);
CREATE INDEX IF NOT EXISTS idx_resources_acq2_predicate ON resources_acq2(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/areas/
-- Table: resources_areas
-- ======================================================================

DROP TABLE IF EXISTS resources_areas CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_areas (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_areas (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 44) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/areas/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_areas_id ON resources_areas(id);
CREATE INDEX IF NOT EXISTS idx_resources_areas_predicate ON resources_areas(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/columns/
-- Table: resources_columns
-- ======================================================================

DROP TABLE IF EXISTS resources_columns CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_columns (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_columns (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 46) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/columns/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_columns_id ON resources_columns(id);
CREATE INDEX IF NOT EXISTS idx_resources_columns_predicate ON resources_columns(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/objspecs/
-- Table: resources_objspecs
-- ======================================================================

DROP TABLE IF EXISTS resources_objspecs CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_objspecs (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_objspecs (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 47) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/objspecs/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_objspecs_id ON resources_objspecs(id);
CREATE INDEX IF NOT EXISTS idx_resources_objspecs_predicate ON resources_objspecs(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/position_
-- Table: resources_position
-- ======================================================================

DROP TABLE IF EXISTS resources_position CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_position (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_position (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 47) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/position_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_position_id ON resources_position(id);
CREATE INDEX IF NOT EXISTS idx_resources_position_predicate ON resources_position(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/prod_
-- Table: resources_prod2
-- ======================================================================

DROP TABLE IF EXISTS resources_prod2 CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_prod2 (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_prod2 (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 43) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/prod_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_prod2_id ON resources_prod2(id);
CREATE INDEX IF NOT EXISTS idx_resources_prod2_predicate ON resources_prod2(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/sites/
-- Table: resources_sites
-- ======================================================================

DROP TABLE IF EXISTS resources_sites CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_sites (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_sites (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 44) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/sites/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_sites_id ON resources_sites(id);
CREATE INDEX IF NOT EXISTS idx_resources_sites_predicate ON resources_sites(predicate);


-- ======================================================================
-- Prefix: https://meta.fieldsites.se/resources/subm_
-- Table: resources_subm2
-- ======================================================================

DROP TABLE IF EXISTS resources_subm2 CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS resources_subm2 (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO resources_subm2 (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 43) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.fieldsites.se/resources/subm_%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_resources_subm2_id ON resources_subm2(id);
CREATE INDEX IF NOT EXISTS idx_resources_subm2_predicate ON resources_subm2(predicate);


-- ======================================================================
-- Prefix: https://meta.icos-cp.eu/collections/
-- Table: meta_icos_cp_eu_collections
-- ======================================================================

DROP TABLE IF EXISTS meta_icos_cp_eu_collections CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS meta_icos_cp_eu_collections (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO meta_icos_cp_eu_collections (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 37) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.icos-cp.eu/collections/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_meta_icos_cp_eu_collections_id ON meta_icos_cp_eu_collections(id);
CREATE INDEX IF NOT EXISTS idx_meta_icos_cp_eu_collections_predicate ON meta_icos_cp_eu_collections(predicate);


-- ======================================================================
-- Prefix: https://meta.icos-cp.eu/dcat/objects/
-- Table: dcat_objects
-- ======================================================================

DROP TABLE IF EXISTS dcat_objects CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS dcat_objects (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO dcat_objects (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 38) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.icos-cp.eu/dcat/objects/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_dcat_objects_id ON dcat_objects(id);
CREATE INDEX IF NOT EXISTS idx_dcat_objects_predicate ON dcat_objects(predicate);


-- ======================================================================
-- Prefix: https://meta.icos-cp.eu/objects/
-- Table: meta_icos_cp_eu_objects
-- ======================================================================

DROP TABLE IF EXISTS meta_icos_cp_eu_objects CASCADE;

-- Table for prefix-specific subjects
CREATE TABLE IF NOT EXISTS meta_icos_cp_eu_objects (
    id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT
);


-- Populate table with data from rdf_triples
INSERT INTO meta_icos_cp_eu_objects (id, predicate, object)
SELECT
    SUBSTRING(subj FROM 33) AS id,
    pred AS predicate,
    obj AS object
FROM rdf_triples
WHERE subj LIKE 'https://meta.icos-cp.eu/objects/%';


-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_meta_icos_cp_eu_objects_id ON meta_icos_cp_eu_objects(id);
CREATE INDEX IF NOT EXISTS idx_meta_icos_cp_eu_objects_predicate ON meta_icos_cp_eu_objects(predicate);


-- ======================================================================
-- Summary: Created 69 tables
-- ======================================================================
