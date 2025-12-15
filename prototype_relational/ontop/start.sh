#!/bin/bash

# # Download DuckDB JDBC driver if not present
# DUCKDB_JDBC="duckdb_jdbc-1.1.3.jar"
# if [ ! -f "$DUCKDB_JDBC" ]; then
#     echo "Downloading DuckDB JDBC driver..."
#     wget https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/1.1.3/$DUCKDB_JDBC
# fi

MAPPING=mapping/generated_all_mappings.obda
# MAPPING=mapping/spec_and_hasName.obda
# MAPPING=mapping/edited_all.obda

# echo "Copying DB"
# cp ../scripts/data/rdfsql.duckdb ontop.duckdb

ONTOP_LOG_LEVEL=DEBUG ../ontop-cli-5.4.0/ontop endpoint \
  --properties="ontop.properties" \
  -l lenses.json \
  --dev \
  --ontology="cpmeta.ttl" \
  --port 65432
  --cors-allowed-origins=* \
  --mapping=$MAPPING \
