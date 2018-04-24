#!/usr/bin/python3
from subprocess import check_output
import os


sparql_query_file = os.path.join(os.getcwd(), 'sparql-query.txt')
sparql_result = check_output(sparql_query_file, shell=True)
lookup = {}

for line in sparql_result.splitlines():
	line_decoded = line.decode("utf-8")
	if not line_decoded == 's,id,name':
		url, id, name = line_decoded.split(',')
		lookup[url] = (id, name)


rdf_query_file = os.path.join(os.getcwd(), 'rdf-query.sql')
rdf_log = check_output("ssh fsicos.lunarc.lu.se docker exec -i rdflogdb-v9.6 psql -U postgres -A -F ',' -t -d postgres < " + rdf_query_file, shell=True)

non_existing_urls = []

for line in rdf_log.splitlines():
	date, url, status = line.decode("utf-8").split(',')

	if url in lookup:
		print (','.join((date, lookup[url][0], lookup[url][1], status)))
	else:
		non_existing_urls.append(url)

if (len(non_existing_urls) > 0):
	print('-' * 150)
	print("Stations not yet labelled:")
	print(', '.join(non_existing_urls))
