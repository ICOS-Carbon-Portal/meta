import PropertyValues from './PropertyValues.js';

export default class ObjectPropertyValues extends PropertyValues{

	constructor(propertyDto, valueDtos){
		super(propertyDto, _.pluck(valueDtos, "value"));
		this._rangeValues = propertyDto.rangeValues || [];
	}

	hasRangeValues(){
		return !_.isEmpty(this._rangeValues);
	}

	getRangeValues(){
		return this._rangeValues;
	}

}

