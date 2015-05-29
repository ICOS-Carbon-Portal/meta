function PropertyValues(){
}

PropertyValues.prototype.getPropertyInfo = function(){
	return this._property.resource;
};

PropertyValues.prototype.getValues = function(){
	return this._values;
};

PropertyValues.prototype.getValuesType = function(){
	return this._property.type;
};

PropertyValues.prototype.getValues = function(){
	return this._values;
};
PropertyValues.prototype.getValidity = function(){
	var valid = false;
	var errors = [];

	var nValues = this._values.length;
	var min = this_.property.cardinality.min;
	var max = this_.property.cardinality.max;

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

module.exports = PropertyValues;
