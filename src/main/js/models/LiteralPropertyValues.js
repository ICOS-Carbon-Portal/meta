var PropertyValues = require('./PropertyValues.js');
var LiteralValue = require('./LiteralValue.js');
var Validation = require('./Validation.js');

function LiteralPropertyValues(property, valueDtos){
	this._property = property;
	this._values = this.makeValues(valueDtos);
}

LiteralPropertyValues.prototype = new PropertyValues();
LiteralPropertyValues.prototype.constructor = LiteralPropertyValues;


LiteralPropertyValues.prototype.makeValues = function(valueDtos){

	var validator = Validation.getValidator(this._property.range);

	return _.map(valueDtos, function(valueDto){

		if(valueDto.type != "literal") throw new Error("Must have been a literal value: " + JSON.stringify(valueDto));

		return new LiteralValue(valueDto.value, validator);
	});
};

module.exports = LiteralPropertyValues;
