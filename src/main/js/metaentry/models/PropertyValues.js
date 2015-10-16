export default class PropertyValues{

	getPropertyDto(){
		return this._propertyDto;
	}

	getPropertyUri(){
		return this._propertyDto.resource.uri;
	}

	getPropertyInfo(){
		return this._propertyDto.resource;
	}

	getValues(){
		return this._values;
	}

	getValuesType(){
		return this._propertyDto.type;
	}

	getValues(){
		return this._values;
	}

	getValidity(){
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
		return {valid: valid, errors: errors}
	}

	canHaveMoreValues(){
		var nValues = this._values.length;
		var max = this._propertyDto.cardinality.max;
		return (_.isUndefined(max) || _.isNumber(max) && (nValues < max));
	}

	getKey(){
		return "prop_" + this.getPropertyUri();
	}

	isRequired(){
		var min = this._propertyDto.cardinality.min;
		return min && (min >= 1);
	}

	isEmpty(){
		return _.isEmpty(this._values);
	}

}

