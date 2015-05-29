var LiteralPropertyValues = require('./LiteralPropertyValues.js');
var ObjectPropertyValues = require('./ObjectPropertyValues.js');

function Individual(individDto){

	this._dto = individDto;
	this._propVals = this.makePropertyValues(individDto);

}

Individual.prototype.getInfo = function(){
	return this._dto.resource;
};

Individual.prototype.getClassInfo = function(){
	return this._dto.owlClass.resource;
};

Individual.prototype.getLabel = function(){
	return this.getInfo().displayName;
};

Individual.prototype.getPropertyValues = function(){
	return this._propVals;
};

Individual.prototype.makePropertyValues = function(individDto){
	var propsToVals = _.groupBy(individDto.values, function(indVal){
		return indVal.property.uri;
	});

	return _.map(this._dto.owlClass.properties, function(prop){

		var values = propsToVals[prop.resource.uri] || [];

		switch(prop.type){
			case "dataProperty": return new LiteralPropertyValues(prop, values);
			case "objectProperty" : return new ObjectPropertyValues(prop, values);
			default: throw new Error("Unknown OWL Property type: " + prop.type);
		}

	});
};

module.exports = Individual;
