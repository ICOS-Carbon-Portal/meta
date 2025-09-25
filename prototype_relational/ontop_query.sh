#!/usr/bin/env bash

HOST="http://localhost:8080/sparql"
curl -X POST \
  --header 'accept: application/csv' \
  --header 'content-type: application/sparql-query' \
  --data @$1 $HOST
