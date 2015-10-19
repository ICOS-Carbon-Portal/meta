import PropertyValues from './PropertyValues.js';
import LiteralValue from './LiteralValue.js';
import Validation from './Validation.js';

function makeValues(valueDtos, propRange){

	var validator = Validation.getValidator(propRange);

	return _.map(valueDtos, valueDto => {

		if(valueDto.type != "literal") throw new Error("Must have been a literal value: " + JSON.stringify(valueDto));

		return new LiteralValue(valueDto.value, validator);
	});
};


export default class DataPropertyValues extends PropertyValues{

	constructor(propertyDto, valueDtos){
		var values = makeValues(valueDtos, propertyDto.range);
		super(propertyDto, values);
	}

}

