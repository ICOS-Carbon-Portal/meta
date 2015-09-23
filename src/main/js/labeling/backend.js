var baseUri = 'http://meta.icos-cp.eu/ontologies/stationentry/';
var lblUri = 'http://meta.icos-cp.eu/ontologies/stationlabeling/';
var ontUri = 'http://meta.icos-cp.eu/ontologies/stationsschema/';

var stationPisQuery = `
PREFIX cpst: <${baseUri}>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT (?s AS ?stationUri) ?theme ?provShortName ?provLongName ?shortName ?longName ?email
FROM NAMED <${baseUri}>
FROM NAMED <${lblUri}>
FROM <${ontUri}>
WHERE{
	?owlClass
		rdfs:subClassOf cpst:Station ;
		rdfs:label ?theme .
	GRAPH <${baseUri}> {
		?s a ?owlClass .
		?s cpst:hasPi ?pi .
		?pi cpst:hasEmail ?email .
		?s cpst:hasShortName ?provShortName .
		?s cpst:hasLongName ?provLongName .
	}
	GRAPH <${lblUri}> {
		OPTIONAL{?s cpst:hasShortName ?shortName }
		OPTIONAL{?s cpst:hasLongName ?longName }
	}
}`;


function postProcessStationsList(stations){
	return _.chain(stations)
		.groupBy(station => station.g + '_' + station.stationUri)
		.values()
		.map(samePi => _.extend(
				_.omit(samePi[0], 'email'), {
					emails: _.pluck(samePi, 'email')
				}
			)
		)
		.map(station => _.extend(
				_.omit(station, 'provShortName', 'provLongName'), {
					shortName: station.shortName || station.provShortName,
					longName: station.longName || station.provLongName
				}
			)
		)
		.sortBy('longName')
		.value();
}

function getStationQuery(stationUri, graphUri){
	return `
		PREFIX cpst: <${baseUri}>
		PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		SELECT DISTINCT (?s AS ?stationUri) ?theme ?longName ?shortName ?lat ?lon ?aboveGround ?aboveSea ?stationClass ?plannedDateStarting
		FROM NAMED <${lblUri}>
		FROM NAMED <${baseUri}>
		FROM <${ontUri}>
		WHERE{
			BIND (<${stationUri}> AS ?s) .
			?owlClass
				rdfs:subClassOf cpst:Station ;
				rdfs:label ?theme .
			GRAPH <${baseUri}> {?s a ?owlClass}
			GRAPH <${graphUri}> {
				OPTIONAL{?s cpst:hasShortName ?shortName}
				OPTIONAL{?s cpst:hasLongName ?longName}
				OPTIONAL{?s cpst:hasLat ?lat}
				OPTIONAL{?s cpst:hasLon ?lon}
				OPTIONAL{?s cpst:hasElevationAboveGround ?aboveGround}
				OPTIONAL{?s cpst:hasElevationAboveSea ?aboveSea}
				OPTIONAL{?s cpst:hasStationClass ?stationClass}
				OPTIONAL{?s cpst:hasOperationalDateEstimate ?plannedDateStarting}
			}
		}`;
}


module.exports = function(ajax, sparql){
	return {
		getStationPis: () => sparql(stationPisQuery).then(postProcessStationsList),

		getProvisionalStationInfo: stationUri => sparql(getStationQuery(stationUri, baseUri)).then(sols => sols[0]),

		getStationLabelingInfo: stationUri => sparql(getStationQuery(stationUri, lblUri)).then(sols => sols[0]),

		whoAmI: () => ajax.getJson('/whoami')
	};
};

