import PropertyValues from './PropertyValues.js';
import LiteralValue from './LiteralValue.js';
import Validation from './Validation.js';

export default class LiteralPropertyValues extends PropertyValues{
	constructor(property, valueDtos){
		super();
		this._propertyDto = property;
		this._values = this.makeValues(valueDtos);
	}

	makeValues(valueDtos){

		var validator = Validation.getValidator(this._propertyDto.range);

		return _.map(valueDtos, valueDto => {

			if(valueDto.type != "literal") throw new Error("Must have been a literal value: " + JSON.stringify(valueDto));

			return new LiteralValue(valueDto.value, validator);
		});
	};

}

