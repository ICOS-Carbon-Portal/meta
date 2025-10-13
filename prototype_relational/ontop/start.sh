#!/bin/bash


../ontop-cli-5.4.0/ontop endpoint \
  --properties="ontop.properties" \
  --ontology="cpmeta.ttl" \
  --port=8080 \
  --cors-allowed-origins=* \
  --mapping="mappings/data_objects.obda"
