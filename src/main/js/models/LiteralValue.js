
function LiteralValue(value, validator){
	this._value = value;
	this._validator = validator;
}

LiteralValue.prototype.getValue = function(){
	return this._value;
};

LiteralValue.prototype.withValue = function(newValue){
	return new LiteralValue(newValue, this._validator);
};

LiteralValue.prototype.getValidity = function(){
	return this._validator(this._value);
};

module.exports = LiteralValue;
