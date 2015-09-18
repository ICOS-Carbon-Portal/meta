'use strict';

var sparqlEndpoint = "/sparql";
var ajax = require('../common/ajax.js');
var sparql = require('../common/sparql.js')(ajax, sparqlEndpoint);

var baseUri = 'http://meta.icos-cp.eu/ontologies/stationentry/';

var stationPisQuery = `
PREFIX cpst: <${baseUri}>
SELECT ?stationUri ?stationClass ?longName ?email
FROM <${baseUri}>
WHERE{
	?stationUri cpst:hasLongName ?longName .
	?stationUri cpst:hasPi ?pi .
	?pi cpst:hasEmail ?email .
	?stationUri a ?stationClass .
}`;

function mapStationTheme(station){
	function classToTheme(stationClass){
		switch(stationClass.substring(stationClass.length - 2)){
			case 'AS': return 'Atmosphere';
			case 'ES': return 'Ecosystem';
			case 'OS': return 'Ocean';
			default: return 'Unknown';
		}
	}
	return _.extend(_.omit(station, 'stationClass'), {
		theme: classToTheme(station.stationClass)
	});
}

module.exports = {
	getStationPis: () => sparql(stationPisQuery).then(stations => stations.map(mapStationTheme))
};

