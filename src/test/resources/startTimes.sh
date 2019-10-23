#!/bin/bash
curl -H "Content-Type: text/plain" -H "Accept: text/csv" -X POST -d @startTimes.rq -o startTimes.csv https://meta.icos-cp.eu/sparql
