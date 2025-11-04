-- Create and populate rdf_triple_values table
-- This table contains triples where the object is a literal value (not an IRI)
-- and excludes predicates that are already stored in the data_objects table

DROP TABLE IF EXISTS rdf_triple_values CASCADE;

CREATE TABLE rdf_triple_values (
    subj TEXT NOT NULL,
    pred TEXT NOT NULL,
    obj TEXT
);

-- Populate with literal values, excluding data_object predicates
INSERT INTO rdf_triple_values (subj, pred, obj)
SELECT subj, pred, obj
FROM rdf_triples
WHERE
    -- Only include literal values (not IRIs)
    obj NOT LIKE 'http://%'
    AND obj NOT LIKE 'https://%'
    AND pred NOT IN (
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasName',
        'http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy',
        'http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasStartTime',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasNumberOfRows',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasActualColumnNames',
        'http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf',
        'http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi',
        'http://meta.icos-cp.eu/ontologies/cpmeta/containsDataset',
        'http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingHeight',
        'http://www.w3.org/ns/prov#startedAtTime',
        'http://www.w3.org/ns/prov#endedAtTime',
        'http://www.w3.org/ns/prov#wasAssociatedWith',
        'http://meta.icos-cp.eu/ontologies/cpmeta/asGeoJSON',
        'http://purl.org/dc/terms/description'
    );

-- Create indices for query performance
CREATE INDEX idx_rdf_triple_values_subj ON rdf_triple_values(subj);
CREATE INDEX idx_rdf_triple_values_pred ON rdf_triple_values(pred);
CREATE INDEX idx_rdf_triple_values_obj ON rdf_triple_values(obj);
