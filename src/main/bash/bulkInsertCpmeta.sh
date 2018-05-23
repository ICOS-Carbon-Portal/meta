#!/bin/bash

BASE='http://127.0.0.1:9094/edit/cpmeta'

function insertRole {
	URI="http://meta.icos-cp.eu/resources/roles/bulk$1"
#	curl -H "Content-Type: application/json" -d '[{"isAssertion": false, "obj": "Bulk role '$1'", "subject": "'$URI'", "predicate": "http://www.w3.org/2000/01/rdf-schema#label"}]' "$BASE/applyupdates"
#	curl -X POST "$BASE/createIndividual?uri=$URI&typeUri=http://meta.icos-cp.eu/ontologies/cpmeta/Role"
	curl -X POST "$BASE/deleteIndividual?uri=$URI&typeUri=http://meta.icos-cp.eu/ontologies/cpmeta/Role"
}

for i in `seq 1 100`;
do
	insertRole $i
done
