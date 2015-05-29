var PropertyValues = require('./PropertyValues.js');

function ObjectPropertyValues(property, valueDtos){
	this._property = property;
	this._values = _.pluck(valueDtos, "value");
}

ObjectPropertyValues.prototype = new PropertyValues();
ObjectPropertyValues.prototype.constructor = ObjectPropertyValues;

module.exports = ObjectPropertyValues;
