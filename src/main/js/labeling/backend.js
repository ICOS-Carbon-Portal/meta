var baseUri = 'http://meta.icos-cp.eu/ontologies/stationentry/';
var lblUri = 'http://meta.icos-cp.eu/ontologies/stationlabeling/';
var ontUri = 'http://meta.icos-cp.eu/ontologies/stationsschema/';
var filesUri = 'http://meta.icos-cp.eu/files/';

var stationPisQuery = `
PREFIX cpst: <${baseUri}>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT *
FROM NAMED <${baseUri}>
FROM NAMED <${lblUri}>
FROM <${ontUri}>
WHERE{
	?owlClass
		rdfs:subClassOf cpst:Station ;
		rdfs:label ?thematicName .
	GRAPH <${baseUri}> {
		?s a ?owlClass .
		?s cpst:hasPi ?pi .
		?pi cpst:hasEmail ?email .
		?s cpst:hasShortName ?provShortName .
		?s cpst:hasLongName ?provLongName .
	}
	OPTIONAL{
		GRAPH <${lblUri}> {
			OPTIONAL{?s cpst:hasShortName ?shortName }
			OPTIONAL{?s cpst:hasLongName ?longName }
		}
	}
}`;


function postProcessStationsList(stations){
	return _.chain(stations)
		.groupBy('s')
		.values()
		.map(samePi => _.extend(
				_.omit(samePi[0], 'email', 'pi'), {
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
		.map(postProcessStationProps)
		.value();
}

function getStationQuery(stationUri, graphUri){
	return `
		PREFIX cpst: <${baseUri}>
		PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		SELECT DISTINCT *
		FROM NAMED <${lblUri}>
		FROM NAMED <${baseUri}>
		FROM <${ontUri}>
		WHERE{
			BIND (<${stationUri}> AS ?s) .
			?owlClass
				rdfs:subClassOf cpst:Station ;
				rdfs:label ?thematicName .
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

function getFilesQuery(stationUri){
	return `
		PREFIX cpst: <${baseUri}>
		PREFIX cpfls: <${filesUri}>
		SELECT DISTINCT ?file ?fileType ?fileName
		FROM <${lblUri}>
		WHERE{
			<${stationUri}> cpst:hasAssociatedFile ?file .
			?file cpfls:hasType ?fileType .
			?file cpfls:hasName ?fileName .
		}
	`;
}

function postProcessStationProps(station){

	var copy = _.omit(station, 's', 'owlClass');

	copy.stationUri = station.s;

	if(station.owlClass === baseUri + 'AS')
		copy.theme = 'Atmosphere';
	else if (station.owlClass === baseUri + 'ES')
		copy.theme = 'Ecosystem';
	else if (station.owlClass === baseUri + 'OS')
		copy.theme = 'Ocean';

	return copy;
}


module.exports = function(ajax, sparql){

	function getStationInfoFromGraph(stationUri, graphUri){
		return sparql(getStationQuery(stationUri, graphUri))
			.then(bindings => bindings.map(postProcessStationProps)[0]);
	}

	function getStationLabelingInfo(stationUri){

		function hasBeenSavedBefore(labelingInfo){
			var essentialProps = _.without(_.keys(labelingInfo), 'stationUri', 'thematicName', 'theme');
			return !_.isEmpty(essentialProps);
		}

		var mainInfo = getStationInfoFromGraph(stationUri, lblUri);
		var filesInfo = sparql(getFilesQuery(stationUri));

		return mainInfo.then(lblInfo =>
			hasBeenSavedBefore(lblInfo)
				? lblInfo
				: getStationInfoFromGraph(stationUri, baseUri)
		).then(stationInfo => filesInfo.then(files => _.extend({files: files}, stationInfo)));
	}

	return {
		getStationPis: () => sparql(stationPisQuery).then(postProcessStationsList),

		getStationInfo: getStationLabelingInfo,
		saveStationInfo: info => ajax.postJson('save', info),
		uploadFile: formData => ajax.uploadFormData('fileupload', formData),

		whoAmI: () => ajax.getJson('/whoami')
	};
};

