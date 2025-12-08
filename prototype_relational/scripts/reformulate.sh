#!/usr/bin/env bash

# HOST="http://localhost:65432/sparql"
HOST="http://localhost:65432/ontop/reformulate"
curl  --get \
  --data-urlencode "query@queries/nores.rq" $HOST
