module.exports = {
	ensureLength: function(maxLength){
		var actualMax = maxLength - 3;
		return function(str){
			return str.length <= actualMax
				? str
				: str.substring(0, actualMax) + "...";
		}
	}
};

