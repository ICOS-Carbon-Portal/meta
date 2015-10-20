export default React.createClass({

	render: function(){
		var rangeValues = this.props.rangeValues;

		if(_.isEmpty(rangeValues)) return null;

		return <div className="panel panel-default">
			<div className="panel-body">
				<form>
					<div className="form-group">
						<label for="newObjPropValue">Choose an entry to be the new value:</label>
						<select className="form-control" id="newObjPropValue" ref="valueSelect">{
							_.map(rangeValues, function(value){
								return <option key={value.uri} value={value.uri}>{value.displayName}</option>;
							})
						}</select>
					</div>
					<button type="button" className="btn btn-default" onClick={this.saveValue}>Save</button>
					<span> </span>
					<button type="button" className="btn btn-default" onClick={this.props.cancelAddition}>Cancel</button>
				</form>
			</div>
		</div>;
	},

	saveValue: function(){
		let value = React.findDOMNode(this.refs.valueSelect).value;
		this.props.saveValue(value);
	}

});

