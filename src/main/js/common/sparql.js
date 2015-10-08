'use strict';

function sparqlBindingValueToPlainJs(lit){
	switch(lit.datatype){
		case "http://www.w3.org/2001/XMLSchema#integer": return parseInt(lit.value);
		case "http://www.w3.org/2001/XMLSchema#float": return parseFloat(lit.value);
		case "http://www.w3.org/2001/XMLSchema#double": return parseFloat(lit.value);
		default: return lit.value;
	}
}

function sparqlJsonToArray(sparqlJson){

	return _.map(sparqlJson.results.bindings, function(binding){

		return _.mapObject(binding, function(value){
			switch(value.type){
				case "uri": return value.value;
				case "literal": return sparqlBindingValueToPlainJs(value);
				default: throw new Error("Unsupported sparql result value type: " + value.type);
			}
		});

	});
}

module.exports = function(ajax, endpoint){

	return function(query){

		return ajax.getJson(endpoint + "?query=" + encodeURIComponent(query))
			.then(function(sparqlJson){
				try{
					return sparqlJsonToArray(sparqlJson);
				} catch (err){
					return Promise.reject(err);
				}
			});
	};

};
