#!/bin/bash

MAPPING=mapping/generated_all_mappings.obda
# MAPPING=mapping/spec_and_hasName.obda
# MAPPING=mapping/edited_all.obda

ONTOP_LOG_LEVEL=DEBUG ../ontop-cli-5.4.0/ontop endpoint \
  --properties="ontop.properties" \
  -l lenses.json \
  --dev \
  --ontology="cpmeta.ttl" \
  --port=8080 \
  --cors-allowed-origins=* \
  --mapping=$MAPPING
