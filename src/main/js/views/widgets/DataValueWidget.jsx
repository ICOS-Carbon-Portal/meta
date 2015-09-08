module.exports = React.createClass({

	getInitialState: function(){
		return {
			dataValue: this.props.dataValue,
			isSaving: false,
			lastPersisted: this.props.dataValue.getValue()
		};
	},

	render: function(){
		var content = this.state.dataValue.getValue();
		var validity = this.state.dataValue.getValidity();
		var persistError = this.state.persistError;
		var style = validity.valid && !persistError ? {} : {backgroundColor: "pink"};
		var title = validity.valid
			? (persistError ? "Saving failed: " + persistError.message : undefined)
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
		var oldValue = this.state.lastPersisted;

		if(value.getValidity().valid && !this.state.readOnly && newValue !== oldValue){
			this.setState({
				isSaving: true,
				beingPersisted: newValue
			});

			var onSuccess = function(){
				var savedValue = this.state.beingPersisted;

				this.setState({
					lastPersisted: savedValue,
					dataValue: value.withValue(savedValue),
					persistError: undefined,
					isSaving: false,
					beingPersisted: undefined
				});
			};

			var onFailure = function(error){
				this.setState({
					persistError: error,
					isSaving: false,
					beingPersisted: undefined
				});
			};
			this.props.replaceValue(this, oldValue, newValue, onSuccess, onFailure);
		}
	}

});

