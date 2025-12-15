#!/usr/bin/env bash

HOST="http://localhost:65432/sparql"
curl -X POST \
  --header 'accept: application/json' \
  --header 'content-type: application/sparql-query' \
  --data @$1 $HOST
