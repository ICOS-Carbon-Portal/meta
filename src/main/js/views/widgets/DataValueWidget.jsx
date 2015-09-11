module.exports = React.createClass({

	getInitialState: function(){
		return {
			dataValue: this.props.dataValue,
			isSaving: false
		};
	},

	render: function(){
		var content = this.state.dataValue.getValue();
		var validity = this.state.dataValue.getValidity();
		var style = validity.valid ? {} : {backgroundColor: "pink"};
		var title = validity.valid
			? undefined
			: validity.errors.join('\n');

		return <input
			type="text" className="form-control" ref="dataValueInput"
			style={style} title={title} readOnly={this.state.isSaving}
			onBlur={this.persistUpdate} value={content} onChange={this.changeHandler}
		/>;
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
	}

});

