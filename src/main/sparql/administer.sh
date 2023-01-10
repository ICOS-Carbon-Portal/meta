#!/bin/bash

curl -X POST --cookie "cpauthToken=..." -d @changeSpecification.rq https://meta.icos-cp.eu/admin/insert/imagezip?dryRun=false

#curl --cookie "cpauthToken=..." --data-urlencode "subject=https://meta.icos-cp.eu/objects/I_HZ0N-B0SOWu_hUiD_MMdlG" -G --data-urlencode "predicate=http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes" https://meta.icos-cp.eu/admin/dropRdf4jTripleObjects

