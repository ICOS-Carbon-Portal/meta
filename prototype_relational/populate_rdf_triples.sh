#!/usr/bin/env bash

psql --host localhost --port 5432 -U postgres -c "\\copy rdf_triples FROM 'dump_full.csv' WITH (format csv);"
