import PropertyValues from './PropertyValues.js';

export default class ObjectPropertyValues extends PropertyValues{
	constructor(property, valueDtos){
		super();
		this._propertyDto = property;
		this._values = _.pluck(valueDtos, "value");
	}
}

