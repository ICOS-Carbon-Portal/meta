export default React.createClass({

	render: function(){
		var rangeValues = this.props.rangeValues;

		if(_.isEmpty(rangeValues)) return null;

		return (
			<form>
				<div className="form-group">
					<label className="form-label" for="newObjPropValue">Choose an entry to be the new value:</label>
					<div className="input-group mb-2">
						<select className="form-select" id="newObjPropValue" ref="valueSelect">{
							_.map(rangeValues, function(value){
								return <option key={value.uri} value={value.uri}>{value.displayName}</option>;
							})
						}</select>
						<button type="button" className="btn btn-secondary" onClick={this.props.cancelAddition}>Cancel</button>
						<button type="button" className="btn btn-primary" onClick={this.saveValue}>Save</button>
					</div>
				</div>
			</form>
		);
	},

	saveValue: function(){
		let value = React.findDOMNode(this.refs.valueSelect).value;
		this.props.saveValue(value);
	}

});

