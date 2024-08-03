import json
from datetime import datetime
from zoneinfo import ZoneInfo
import requests
from dataclasses import dataclass
from typing import Optional, Tuple, Any


SPARQL_ENDPOINT = "https://meta.icos-cp.eu/sparql"


@dataclass
class SubmissionWindow:
	start: datetime
	end: datetime

@dataclass
class SparqlResults:
	params: list[str]
	bindings: list[dict[str, dict[str, str | int | float]]]


def run_sparql_select_query(query: str) -> Optional[SparqlResults]:
	"""Run a SPARQL SELECT query on the ICOS Carbon Portal SPARQL endpoint.
	
	Parameters
	----------
	query : str
		SPARQL query.
	
	Returns
	-------
		The results of the query in the form of a SparqlResults object containing
		the list of parameters and the list of bindings.
		If the HTTP response's status code is not 200, returns an HTTPError.
	"""

	resp = requests.get(SPARQL_ENDPOINT, params={"query": query})
	if resp.status_code == 200:
		content = json.loads(resp.text)
		return SparqlResults(
			params=content["head"]["vars"],
			bindings=content["results"]["bindings"]
		)
	elif not resp.ok:
		raise requests.HTTPError(
			f"Error {resp.status_code} when running SPARQL query\n{query}\n"
			f"at SPARQL endpoint {SPARQL_ENDPOINT}.\nReason: {resp.reason}"
		)
	else:
		raise requests.HTTPError(
			f"HTTP status code {resp.status_code} when running SPARQL query"
			f"\n{query}\n at SPARQL endpoint {SPARQL_ENDPOINT}.\nReason: {resp.reason}"
		)


def run_sparql_select_query_single_param(query: str, result_type: Optional[type]=None) -> list[Any]:
	sparql_results = run_sparql_select_query(query)
	if sparql_results is None:
		return []
	if len(sparql_results.params) == 1:
		param = sparql_results.params[0]
		results: list[str | int | float] = []
		for binding in sparql_results.bindings:
			value = check_value_type(binding[param]["value"], result_type, query)
			results.append(value)
		return results
	else:
		raise TypeError(
			"Only one parameter is expected as a result of SPARQL query"
			f"\n{query}\nbut zero or more than one were returned."
		)


def run_sparql_select_query_multi_params(query: str, result_type: Optional[type]=None) -> Optional[dict[str, list[str | int | float]]]:
	sparql_results = run_sparql_select_query(query)
	if sparql_results is None:
		return {}
	if len(sparql_results.params) > 1:
		results: dict[str, list[str | int | float]] = {}
		for param in sparql_results.params:
			results[param] = []
			for binding in sparql_results.bindings:
				value = check_value_type(binding[param]["value"], result_type, query)
				results[param].append(value)
		return results
	else:
		raise TypeError(
			"More than one parameters were expected as a result of SPARQL query"
			f"\n{query}\nbut zero or one was returned."
		)


def check_value_type(value: Any, expected_type: Optional[type], query: str) -> Any:
	if expected_type is not None and not isinstance(value, expected_type):
		raise TypeError(
			f"Results of SPARQL query\n{query}\nare expected to be of type"
			f"{expected_type} but type {type(value)} was returned."
		)
	else: return value


def submission_window_to_str(submission_window: SubmissionWindow, fmt: str, tz: ZoneInfo) -> Tuple[str, str]:
	earliest = submission_window.start.astimezone(tz).strftime(fmt)
	latest = submission_window.end.astimezone(tz).strftime(fmt)
	return earliest, latest


def submission_window_to_utc_str(submission_window: SubmissionWindow, fmt: str) -> Tuple[str, str]:
	return submission_window_to_str(submission_window, fmt, ZoneInfo("UTC"))


def obspack_time_series_query(submission_window: SubmissionWindow) -> str:
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
	""" % submission_window_to_utc_str(submission_window, "%Y-%m-%dT%H:%M:%SZ")


def obspack_release_query(object_spec: str, submission_window: SubmissionWindow) -> str:
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
SELECT ?doi WHERE {
	VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/%s>}
	?dobj cpmeta:hasObjectSpec ?spec .
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	?dobj cpmeta:hasDoi ?doi .
	FILTER( ?submTime >= '%s'^^xsd:dateTime && ?submTime <= '%s'^^xsd:dateTime )
}
ORDER BY DESC(?submTime)
	""" % (object_spec, *submission_window_to_utc_str(submission_window, "%Y-%m-%dT%H:%M:%SZ"))


def instrument_query(instrument_atc_id: int) -> str:
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
SELECT ?instrumentInfo WHERE {
	VALUES ?instrument { <http://meta.icos-cp.eu/resources/instruments/ATC_%s> }
	?instrument cpmeta:hasModel ?model .
	?instrument cpmeta:hasSerialNumber ?serialNumber .
	?instrument cpmeta:hasVendor/cpmeta:hasName ?vendorName .
	BIND(concat(?vendorName, ", ", ?model, ", ", ?serialNumber) AS ?instrumentInfo)
}
	""" % instrument_atc_id


def contributor_roles_query(contributor_uri: str, station_uri: str) -> str:
	return """
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?organizationLabel ?role WHERE {
	VALUES ?contributor { <%s> }
	VALUES ?organization { <%s> }
	?contributor cpmeta:hasMembership ?membership .
	?membership cpmeta:atOrganization ?organization .
	?organization rdfs:label ?organizationLabel .
	?membership cpmeta:hasRole/rdfs:label ?role .
}
	""" % (contributor_uri, station_uri)