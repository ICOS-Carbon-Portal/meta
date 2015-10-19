export default React.createClass({

	render: function(){
		var rangeValues = this.props.rangeValues;

		if(_.isEmpty(rangeValues)) return null;

		return <select>{
			_.map(rangeValues, function(value){
				return <option key={value.uri} value={value.uri}>{value.displayName}</option>;
			})
		}</select>;

	}
});

