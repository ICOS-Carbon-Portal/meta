import {baseUri, lblUri, ontUri, filesUri, stationOwlClassToTheme} from './configs.js';

function getOptionals(propNames){
	return propNames
		.map(propName => `OPTIONAL{?s cpst:${propName} ?${propName}}`)
		.join('\n');
}

const stationPisQuery = `
	PREFIX cpst: <${baseUri}>
	PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
	SELECT *
	FROM NAMED <${baseUri}>
	FROM NAMED <${lblUri}>
	FROM <${ontUri}>
	WHERE{
		?owlClass rdfs:subClassOf cpst:Station .
		GRAPH <${baseUri}> {
			?s a ?owlClass .
			?s cpst:hasPi ?pi .
			?pi cpst:hasEmail ?email .
			?s cpst:hasShortName ?provShortName .
			?s cpst:hasLongName ?provLongName .
		}
		OPTIONAL{
			GRAPH <${lblUri}> {
				${getOptionals(['hasShortName', 'hasLongName', 'hasApplicationStatus'])}
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
					hasShortName: station.hasShortName || station.provShortName,
					hasLongName: station.hasLongName || station.provLongName
				}
			)
		)
		.map(postProcessStationProps)
		.value();
}

function getStationQuery(stationUri, graphUri){
	let commonPropNames = ['hasLat', 'hasLon', 'hasApplicationStatus'];
	let propNames = {
		AS: ['hasShortName', 'hasLongName', 'hasMainPersonnelNamesList', 'hasResponsibleInstitutionName',
			'hasAddress', 'hasWebsite', 'hasStationClass', 'hasElevationAboveGround',
			'hasElevationAboveSea', 'hasAccessibility', 'hasVegetation', 'hasAnthropogenics',
			'hasConstructionStartDate', 'hasConstructionEndDate', 'hasOperationalDateEstimate',
			'hasTelecom', 'hasExistingInfrastructure', 'hasNameListOfNetworksItBelongsTo'],
		ES: ['hasAnemometerDirection', 'hasEddyHeight'],
		OS: ['hasShortName']
	};
	let union = _.map(propNames, (classProps, uriEnd) => `{
		${getOptionals(commonPropNames.concat(classProps))}
		GRAPH <${baseUri}> {
			?s a ?owlClass .
			FILTER STRENDS(STR(?owlClass), "${uriEnd}")
		}
	}`).join('\nUNION\n');

	return `
PREFIX cpst: <${baseUri}>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT *
FROM NAMED <${lblUri}>
FROM NAMED <${baseUri}>
FROM <${ontUri}>
WHERE{
	BIND (<${stationUri}> AS ?s) .
	?owlClass rdfs:subClassOf cpst:Station .
	GRAPH <${graphUri}> {
		{
			${union}
		}
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
	return _.extend(
		_.omit(station, 's', 'owlClass'), {
			stationUri: station.s,
			theme: stationOwlClassToTheme(station.owlClass)
		}
	);
}


module.exports = function(ajax, sparql){

	function getStationInfoFromGraph(stationUri, graphUri){
		return sparql(getStationQuery(stationUri, graphUri))
			.then(bindings => bindings.map(postProcessStationProps)[0]);
	}

	function getStationFiles(stationUri){
		return sparql(getFilesQuery(stationUri));
	}

	function getStationLabelingInfo(stationUri){

		function hasBeenSavedBefore(labelingInfo){
			var essentialProps = _.without(_.keys(labelingInfo), 'stationUri', 'theme');
			return !_.isEmpty(essentialProps);
		}

		var mainInfo = getStationInfoFromGraph(stationUri, lblUri);
		var filesInfo = getStationFiles(stationUri);

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
		updateStatus: (stationUri, newStatus) => ajax.postJson('updatestatus', {stationUri, newStatus}),

		getStationFiles: getStationFiles,
		uploadFile: formData => ajax.uploadFormData('fileupload', formData),
		deleteFile: fileInfo => ajax.postJson('filedeletion', fileInfo),

		whoAmI: () => ajax.getJson('userinfo'),
		saveUserInfo: userInfo => ajax.postJson('saveuserinfo', userInfo)
	};
};

