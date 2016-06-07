export default {
	ensureLength: function(maxLength){
		var actualMax = maxLength - 3;
		return function(str){
			return str.length <= maxLength
				? str
				: str.substring(0, actualMax) + "...";
		}
	}
};

