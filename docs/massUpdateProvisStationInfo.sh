#!/bin/bash

#HOST='http://127.0.0.1:9094'
HOST='https://meta.icos-cp.eu'

STATIONCLASS='http://meta.icos-cp.eu/ontologies/stationentry/ES'
PI='http://meta.icos-cp.eu/resources/stationentry/sweetdario'
PIPROP='http://meta.icos-cp.eu/ontologies/stationentry/hasPi'

QUERY="select ?s where{?s a <$STATIONCLASS> FILTER NOT EXISTS{?s <$PIPROP> <$PI>}}"

STATIONS=`curl --silent -X POST -H "Content-Type: text/plain" -H "Accept: text/csv" -d "$QUERY" "$HOST/sparql" | dos2unix | tail -n +2`

while read -r STATION; do
JSON=$(cat <<EOF
[
	{
		"isAssertion": true,
		"subject": "$STATION",
		"predicate": "$PIPROP",
		"obj":"$PI"
	}
]
EOF
)
	echo "$JSON"
	curl --cookie cookies.txt -X POST -H "Content-Type: application/json" -d "$JSON" "$HOST"/edit/stationentry/applyupdates
done < <(echo "$STATIONS")

