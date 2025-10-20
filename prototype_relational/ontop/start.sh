#!/bin/bash


../ontop-cli-5.4.0/ontop endpoint \
  --properties="ontop.properties" \
  --dev \
  --ontology="cpmeta.ttl" \
  --port=8080 \
  --cors-allowed-origins=* \
  --mapping="mappings.obda"
