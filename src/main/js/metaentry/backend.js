'use strict';

import ajax from '../common/ajax.js';

export default {
	listClasses(){
		return ajax.getJson('getExposedClasses');
	},

	listIndividuals(classUri){
		var url = 'listIndividuals?classUri=' + encodeURIComponent(classUri);
		return ajax.getJson(url);
	},

	getIndividual(uri){
		var url = 'getIndividual?uri=' + encodeURIComponent(uri);
		return ajax.getJson(url);
	},

	checkSuffix(baseClass, suffix){
		var uri = baseClass + encodeURI(suffix);
		var url = 'checkIfUriIsFree?uri=' + encodeURIComponent(uri);

		return ajax.getJson(url).then(function(isAvailable){
			return {candidateUri: uri, suffixAvailable: isAvailable};
		});
	},

	createIndividual(uri, rdfType){
		var url = ['createIndividual?uri=', encodeURIComponent(uri), '&typeUri=', encodeURIComponent(rdfType)].join('');
		return ajax.postJson(url, {}); //dummy payload
	},

	deleteIndividual(uri){
		var url = 'deleteIndividual?uri=' + encodeURIComponent(uri);
		return ajax.postJson(url, {}); //dummy payload
	},

	applyUpdates(updates){
		return ajax.postJson('applyupdates', updates);
	},

	performReplacement(replacement){
		return ajax.postJson('performreplacement', replacement);
	},

	getRangeValues(individClassUri, propUri){
		var url = ['getRangeValues?classUri=', encodeURIComponent(individClassUri), '&propUri=', encodeURIComponent(propUri)].join('');
		return ajax.getJson(url);
	}
};

