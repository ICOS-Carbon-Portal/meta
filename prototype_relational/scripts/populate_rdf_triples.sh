#!/usr/bin/env bash

export PGPASSWORD=ontop
psql --host localhost --port 5432 -U postgres -c "CREATE TABLE rdf_triples (subj TEXT, pred TEXT, obj TEXT);"
psql --host localhost --port 5432 -U postgres -c "\\copy rdf_triples FROM '../dumps/dump_full.csv' WITH (format csv);"
#
# Purge fieldsites
psql --host localhost --port 5432 -U postgres -c "delete from rdf_triples where subj like 'https://meta.fieldsites.se%' or pred like 'https://meta.fieldsites.se%' or obj like 'https://meta.fieldsites.se%';"

# Index
psql --host localhost --port 5432 -U postgres -c "create index on rdf_triples(subj);"
psql --host localhost --port 5432 -U postgres -c "create index on rdf_triples(pred);"
