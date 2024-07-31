import json
import requests
import warnings


def run_query(query: str) -> list[str | int | float] | dict[str, list[str | int | float]]:
	"""Run a SPARQL SELECT query on the ICOS Carbon Portal SPARQL endpoint.
	
	Parameters
	----------
	query : str
		SPARQL query.
	
	Returns
	-------
		A dictionary containing the results of the query in the form
		of a dictionary where each key corresponds to a parameter and
		each value contains the list of values for the parameter.
		If the result of the query contains only one parameter, returns
		the list of values for that parameter.
		If the HTTP response's status code is not 200, returns an empty list.
	"""
	
	sparql_endpoint = "https://meta.icos-cp.eu/sparql"
	resp = requests.get(sparql_endpoint, params={"query": query})
	if resp.status_code == 200:
		content = json.loads(resp.text)
		params = content["head"]["vars"]
		bindings = content["results"]["bindings"]
		results: list[str | int | float] | dict[str, list[str | int | float]]
		if len(params) == 1:
			results = []
			for binding in bindings:
				results.append(binding[params[0]]["value"])
		else:
			results = {}
			for param in params:
				results[param] = []
				for binding in bindings:
					results[param].append(binding[param]["value"])
		return results
	else:
		warnings.warn(
			f"Error 404 when running SPARQL query\n{query}\n"
			f"at SPARQL endpoint {sparql_endpoint}."
		)
		return []


def collection_query(collection_pid: str) -> str:
	
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX dcterms: <http://purl.org/dc/terms/>
SELECT ?dobj_url WHERE {
	VALUES ?collection { <https://meta.icos-cp.eu/collections/%s> }
	?collection dcterms:hasPart ?dobj_url .
}
	""" % collection_pid


def instrument_query(instrument_url: str) -> str:
	
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
SELECT ?instrument ?vendorName ?model ?serialNumber WHERE {
	VALUES ?instrument { <%s> }
	?instrument cpmeta:hasModel ?model .
	?instrument cpmeta:hasSerialNumber ?serialNumber .
	?instrument cpmeta:hasVendor ?vendor .
	?vendor cpmeta:hasName ?vendorName .
}
	""" % instrument_url


def contributors_query(dataset_url: str) -> str:
	
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
SELECT ?contributor ?organizationLabel ?roleLabel WHERE {
	VALUES ?ds { <%s> }
	?ds cpmeta:wasProducedBy ?prod .
	?prod cpmeta:wasParticipatedInBy ?prodContrib .
	?prodContrib rdf:_1|rdf:_2|rdf:_3|rdf:_4|rdf:_5|rdf:_6|rdf:_7|rdf:_8|rdf:_9|rdf:_10 ?contributor .
	?contributor cpmeta:hasMembership ?membership .
	?membership cpmeta:atOrganization ?organization .
	?membership cpmeta:hasRole ?role .
	?organization rdfs:label ?organizationLabel .
	?role rdfs:label ?roleLabel .
}
	""" % dataset_url