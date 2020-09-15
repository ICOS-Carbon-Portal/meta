#!/bin/bash
tail -n +2 ./NRT-to-deprecate.csv | \
awk -F "," '
BEGIN{
	print "prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>"
	print "construct{"
}
{print "<"$14"> cpmeta:isNextVersionOf <"$7"> ."}
END{
	print "}"
	print "where{}"
}
' | sed 's/11676\//https:\/\/meta.icos-cp.eu\/objects\//g'
