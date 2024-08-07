var ok = {valid: true, errors: []};

function error(message){
	return {valid: false, errors: _.flatten([message])};
}

function stringValidator(s){
	return ok;
}

function intValidator(s){
	return /\-?\d+/.test(s) ? ok : error("Not a valid integer!");
}

function urlValidator(s){
	var expression = /https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)/gi;
	var regex = new RegExp(expression);
	return (s && s.match(regex)) ? ok : error("Not a valid URL!");
}

function isoDateTimeValidator(s){
	var expression = /^(-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[01]|0[1-9]|[12][0-9])T(2[0-3]|[01][0-9]):([0-5][0-9]):([0-5][0-9])(.[0-9]+)?(Z|[+-][01][0-8]:[03]0)$/g;
	var regex = new RegExp(expression);
	return (s && s.match(regex)) ? ok : error(["Not a valid dateTime!", "Valid examples: 2017-06-01T18:43:26+01:00 or 2017-06-01T17:43:26Z"]);
}

function isoDateValidator(s){
	var expression = /^(-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[01]|0[1-9]|[12][0-9])$/g;
	var regex = new RegExp(expression);
	return (s && s.match(regex)) ? ok : error(["Not a valid date!", "Valid examples: 2017-06-01 or 2017-06-01"]);
}

function boolValidator(s){
	var boolError = error("Must be 'true' or 'false'");
	if(_.isEmpty(s)) return boolError;
	var sl = s.toLowerCase();
	return (sl === "true" || sl === "false") ? ok : boolError;
}

function doubleValidator(s){
	return (_.isNaN(parseFloat(s)) || !isFinite(s)) ? error("Not a number!") : ok;
}

function minValueValidator(min){
	return function(s){
		var number = parseFloat(s);
		return number >= min ? ok : error("Must be more than or equal to " + min);
	};
}

function maxValueValidator(max){
	return function(s){
		var number = parseFloat(s);
		return number <= max ? ok : error("Must be less than or equal to " + max);
	};
}

function regexpValueValidator(pattern){
	var regex = new RegExp(pattern);
	return function(s){
		return regex.test(s) ? ok : error([
			"Invalid string format.",
			"Must follow regular expression " + pattern
		]);
	};
}

function oneOfValueValidator(allowedStrings){
	return function(s){
		return _.contains(allowedStrings, s) ? ok : error("Must be one of: " + allowedStrings.join(', '));
	};
}

var xsd = "http://www.w3.org/2001/XMLSchema#";
var rdfs = "http://www.w3.org/2000/01/rdf-schema#";

var dataTypeValidators = _.object([
	[xsd + "string", stringValidator],
	[rdfs + "Literal", stringValidator],
	[xsd + "integer", intValidator],
	[xsd + "boolean", boolValidator],
	[xsd + "double", doubleValidator],
	[xsd + "float", doubleValidator],
	[xsd + "dateTime", isoDateTimeValidator],
	[xsd + "date", isoDateValidator],
	[xsd + "anyURI", urlValidator]
]);

function conditionalValidator(conditionValidator, otherValidators){
	return function(s){
		var conditionValidity = conditionValidator(s);

		if(!conditionValidity.valid) return conditionValidity;

		return aggregatingValidator(otherValidators)(s);
	};
}

function aggregatingValidator(subValidators){
	return function(s){
		return aggregateValidities(
			_.map(subValidators, function(validator){
				return validator(s);
			})
		);
	};
}

function aggregateValidities(validities){
	return {
		valid: _.every(validities, _.property("valid")),
		errors: _.flatten(_.pluck(validities, "errors"))
	};
}


var restrictionValidators = _.object([
	["minValue", minValueValidator],
	["maxValue", maxValueValidator],
	["regExp", regexpValueValidator],
	["oneOf", oneOfValueValidator]
]);

function getValidator(dataRangeDto){

	var dtype = dataRangeDto.dataType;
	var dtypeValidator = dataTypeValidators[dtype];
	if(!dtypeValidator) throw new Error("Unsupported data type: '" + dtype + "'");

	var otherValidators = _.map(dataRangeDto.restrictions || [], function(restriction){

		switch(restriction.type){
			case "minValue": return minValueValidator(restriction.minValue);
			case "maxValue": return maxValueValidator(restriction.maxValue);
			case "regExp":   return regexpValueValidator(restriction.regexp);
			case "oneOf":    return oneOfValueValidator(restriction.values);
			default:         throw new Error("Unsupported data type restriction: '" + restriction.type + "'");
		}

	});

	return conditionalValidator(dtypeValidator, otherValidators);
}

module.exports = {
	getValidator,
	aggregateValidities,
	urlValidator
};


