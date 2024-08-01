import json
from datetime import datetime
from zoneinfo import ZoneInfo
import requests
import warnings
from dataclasses import dataclass


@dataclass
class SubmissionWindow:
	start: datetime
	end: datetime


def run_query(query: str) -> list[str | int | float] | dict[str, list[str | int | float]]:
	"""Run a SPARQL SELECT query on the ICOS Carbon Portal SPARQL endpoint.
	
	Parameters
	----------
	query : str
		SPARQL query.
	
	Returns
	-------
		The results of the query in the form of a dictionary where each key
		corresponds to a parameter and each value contains the list of values
		for the parameter.
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


def obspack_time_series_query(submission_window: SubmissionWindow) -> str:
	fmt = "%Y-%m-%dT%H:%M:%SZ"
	utc = ZoneInfo("UTC")
	earliest = submission_window.start.astimezone(utc).strftime(fmt)
	latest = submission_window.end.astimezone(utc).strftime(fmt)
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
SELECT ?dobj WHERE {
	VALUES ?spec { <http://meta.icos-cp.eu/resources/cpmeta/ObspackTimeSerieResult> <http://meta.icos-cp.eu/resources/cpmeta/ObspackCH4TimeSeriesResult> <http://meta.icos-cp.eu/resources/cpmeta/ObspackN2oTimeSeriesResult> }
	?dobj cpmeta:hasObjectSpec ?spec .
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	FILTER( ?submTime >= '%s'^^xsd:dateTime && ?submTime <= '%s'^^xsd:dateTime )
}
	""" % (earliest, latest)


def instrument_query(instrument_atc_id: int) -> str:
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
SELECT ?instrumentInfo WHERE {
	VALUES ?instrument { <http://meta.icos-cp.eu/resources/instruments/ATC_%s> }
	?instrument cpmeta:hasModel ?model .
	?instrument cpmeta:hasSerialNumber ?serialNumber .
	?instrument cpmeta:hasVendor ?vendor .
	?vendor cpmeta:hasName ?vendorName .
	BIND(concat(?vendorName, ", ", ?model, ", ", ?serialNumber) AS ?instrumentInfo)
}
	""" % instrument_atc_id


def contributors_query(dataset_url: str) -> str:
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?contributor ?email ?organizationLabel ?roleLabel WHERE {
	VALUES ?ds { <%s> }
	?ds cpmeta:wasProducedBy ?prod .
	?prod cpmeta:wasParticipatedInBy ?prodContrib .
	?prodContrib rdf:_1|rdf:_2|rdf:_3|rdf:_4|rdf:_5|rdf:_6|rdf:_7|rdf:_8|rdf:_9|rdf:_10 ?contributor .
	?contributor cpmeta:hasMembership ?membership .
	?contributor cpmeta:hasEmail ?email .
	?membership cpmeta:atOrganization ?organization .
	?membership cpmeta:hasRole ?role .
	?organization rdfs:label ?organizationLabel .
	?role rdfs:label ?roleLabel .
}
	""" % dataset_url