export default class LiteralValue{

	constructor(value, validator){
		this._value = value;
		this._validator = validator;
	}

	getValue(){
		return this._value;
	};

	withValue(newValue){
		return new LiteralValue(newValue, this._validator);
	};

	getValidity(){
		return this._validator(this._value);
	};

}

