import PropertyValues from './PropertyValues.js';

export default class ObjectPropertyValues extends PropertyValues{

	constructor(propertyDto, valueDtos){
		super(propertyDto, _.pluck(valueDtos, "value"));
		this._rangeValues = propertyDto.rangeValues || null;
	}

	hasRangeValues(){
		return this._rangeValues != null && !_.isEmpty(this._rangeValues);
	}

	getRangeValues(){
		return this._rangeValues;
	}

}

