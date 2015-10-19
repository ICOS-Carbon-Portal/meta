import DataPropertyValues from './DataPropertyValues.js';
import ObjectPropertyValues from './ObjectPropertyValues.js';

function sortAlphabetically(propValsGroup){
	return _.sortBy(propValsGroup, function(propVals){
		return propVals.getPropertyInfo().displayName.toLowerCase();
	});
}

function makePropertyValues(individDto){
	var propsToVals = _.groupBy(individDto.values, function(indVal){
		return indVal.property.uri;
	});

	var unordered = _.map(individDto.owlClass.properties, function(propDto){

		var values = propsToVals[propDto.resource.uri] || [];

		switch(propDto.type){
			case "dataProperty": return new DataPropertyValues(propDto, values);
			case "objectProperty" : return new ObjectPropertyValues(propDto, values);
			default: throw new Error("Unknown OWL Property type: " + propDto.type);
		}

	});

	return _.chain(unordered)
		.partition(function(propVal){
			return propVal.isRequired();
		})
		.map(sortAlphabetically)
		.flatten()
		.value();
}

export default class Individual{

	constructor(individDto){
		this._dto = individDto;
		this._propVals = makePropertyValues(individDto);
	}

	getInfo(){
		return this._dto.resource;
	}

	getClassInfo(){
		return this._dto.owlClass.resource;
	}

	getLabel(){
		return this.getInfo().displayName;
	}

	getPropertyValues(){
		return this._propVals;
	}

	getKey(){
		return "ind_" + this.getInfo().uri;
	}

}

