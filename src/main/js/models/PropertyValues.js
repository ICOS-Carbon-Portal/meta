function PropertyValues(){
}

PropertyValues.prototype.getPropertyDto = function(){
	return this._propertyDto;
};

PropertyValues.prototype.getPropertyUri = function(){
	return this._propertyDto.resource.uri;
};

PropertyValues.prototype.getPropertyInfo = function(){
	return this._propertyDto.resource;
};

PropertyValues.prototype.getValues = function(){
	return this._values;
};

PropertyValues.prototype.getValuesType = function(){
	return this._propertyDto.type;
};

PropertyValues.prototype.getValues = function(){
	return this._values;
};
PropertyValues.prototype.getValidity = function(){
	var valid = false;
	var errors = [];

	var nValues = this._values.length;
	var min = this._propertyDto.cardinality.min;
	var max = this._propertyDto.cardinality.max;

	if(min && max && min == max && nValues != min){
		errors.push("Must have exactly " + min + " value" + (min == 1 ? "." : "s.") );
	}
	else if(min && nValues < min){
		errors.push("At least " + min + " value" + (min == 1 ? " is" : "s are") + " required.");
	}
	else if(max && nValues > max){
		errors.push("At most " + max + " value" + (max == 1 ? " is" : "s are") + " allowed.");
	}
	else {
		valid = true;
	}
	return {valid: valid, errors: errors};
};

PropertyValues.prototype.getKey = function(){
	return "prop_" + this.getPropertyUri();
};

PropertyValues.prototype.isRequired = function(){
	var min = this._propertyDto.cardinality.min;
	return min && (min >= 1);
};

PropertyValues.prototype.isEmpty = function(){
	return _.isEmpty(this._values);
};

module.exports = PropertyValues;
