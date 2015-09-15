
function stateFromNewProps(props){
	return {
		dataValue: props.dataValue,
		isSaving: false
	};
}

module.exports = React.createClass({

	getInitialState: function(){
		return stateFromNewProps(this.props);
	},

	componentWillReceiveProps: function(newProps){
		this.setState(stateFromNewProps(newProps));
	},

	render: function(){
		var content = this.state.dataValue.getValue();
		var validity = this.state.dataValue.getValidity();
		var style = validity.valid ? {} : {backgroundColor: "pink"};
		var title = validity.valid
			? undefined
			: validity.errors.join('\n');

		return <div className="input-group">
			<input
				type="text" className="form-control" ref="dataValueInput"
				style={style} title={title} readOnly={this.state.isSaving}
				onBlur={this.persistUpdate} value={content} onChange={this.changeHandler}
				onKeyDown={this.keyDownHandler}
			/>
			<span className="input-group-btn">
				<button className="btn btn-default" type="button" onClick={this.deleteSelf}>
					<span className="glyphicon glyphicon-remove"></span>
				</button>
			</span>
		</div>;
	},

	changeHandler: function(){
		var elem = React.findDOMNode(this.refs.dataValueInput);
		var newValueStr = elem.value;
		var currentValue = this.state.dataValue;
		if(currentValue.getValue() != newValueStr) {
			var updatedValue = currentValue.withValue(newValueStr);
			this.setState({dataValue: updatedValue});
		}
	},
	
	keyDownHandler: function(event){
		if(event.keyCode == 13){
			var elem = React.findDOMNode(this.refs.dataValueInput);
			elem.blur();
		}
	},

	persistUpdate: function(){
		var value = this.state.dataValue;
		var newValue = value.getValue();
		var oldValue = this.props.dataValue.getValue();

		if(value.getValidity().valid && !this.state.isSaving && newValue !== oldValue){

			this.props.requestUpdate({
				updates: [{
					isAssertion: false,
					obj: oldValue
				}, {
					isAssertion: true,
					obj: newValue
				}]
			});

			this.setState({
				isSaving: true
			});

		}
	},

	deleteSelf: function(){
		var value = this.props.dataValue.getValue();
		this.props.requestUpdate({
			updates: [{
				isAssertion: false,
				obj: value
			}]
		});
	}

});

