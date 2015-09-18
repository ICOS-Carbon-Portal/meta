'use strict';

var ajax = require('../common/ajax.js');

function stripSlash(s){
	if(s.endsWith('/')) return stripSlash(s.substr(0, s.length - 1));
	else return s;
}

module.exports = {
	listClasses: function(){
		return ajax.getJson('getExposedClasses');
	},
	listIndividuals: function(classUri){
		var url = 'listIndividuals?classUri=' + encodeURIComponent(classUri);
		return ajax.getJson(url);
	},
	getIndividual: function(uri){
		var url = 'getIndividual?uri=' + encodeURIComponent(uri);
		return ajax.getJson(url);
	},
	checkSuffix: function(baseClass, suffix){
		var uri = stripSlash(baseClass) + '/' + encodeURI(suffix);
		var url = 'checkIfUriIsFree?uri=' + encodeURIComponent(uri);

		return ajax.getJson(url).then(function(isAvailable){
			return {candidateUri: uri, suffixAvailable: isAvailable};
		});
	},
	createIndividual: function(uri, rdfType){
		var url = ['createIndividual?uri=', encodeURIComponent(uri), '&typeUri=', encodeURIComponent(rdfType)].join('');
		return ajax.postJson(url, {}); //dummy payload
	},
	deleteIndividual: function(uri){
		var url = 'deleteIndividual?uri=' + encodeURIComponent(uri);
		return ajax.postJson(url, {}); //dummy payload
	},
	applyUpdates: function(updates){
		return ajax.postJson('applyupdates', updates);
	},
	performReplacement: function(replacement){
		return ajax.postJson('performreplacement', replacement);
	}
};

