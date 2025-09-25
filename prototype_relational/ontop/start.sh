#!/bin/bash


../ontop-cli-5.3.0/ontop endpoint \
  --properties="ontop.properties" \
  --ontology="cpmeta.ttl" \
  --port=8080 \
  --cors-allowed-origins=* \
  --mapping="data_objects.obda"
