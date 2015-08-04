var Widget = React.createClass({

	render: function(){

		var cssClasses = "panel panel-" + this.props.widgetType;

		return <div className={cssClasses}>
			<div className="panel-heading" title={this.props.headerTitle}>
				<h3 className="panel-title">{this.props.widgetTitle}</h3><
			/div>
			<div className="panel-body">{this.props.children}</div>
		</div>;
	}

});

var StaticWidget = React.createClass({

	render: function(){

		return <Widget
			widgetTitle={this.props.widgetTitle}
			widgetType="success">
			{this.props.children}
		</Widget>;
	}

});

var DataValueWidget = React.createClass({
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

var DataPropertyWidget = React.createClass({
	render: function(){
		var props = this.props;
		var propValues = props.propertyValues;
		var predicate = propValues.getPropertyInfo().uri;

		return <Widget {...this.props}>{
			_.map(propValues.getValues(), function(value, i){
				var key = propValues.getKey() + "_val_" + i;

				var subject = props.subjectUri;
				var replaceValue = getDataValueReplacer(props.backend, subject, predicate);

				return <DataValueWidget key={key} dataValue={value} replaceValue={replaceValue}/>;
			})
		}</Widget>;

	}
});

var ObjectPropertyWidget = React.createClass({
	render: function(){
		var propValues = this.props.propertyValues;

		return <Widget {...this.props}>{
			_.map(propValues.getValues(), function(value, i){
				var key = propValues.getKey() + "_val_" + i;
				return <div key={key}>{value.displayName}</div>;
			})
		}</Widget>;

	}
});

var PropertyWidget = React.createClass({

	render: function(){

		var propValues = this.props.propertyValues;
		var validity = propValues.getValidity();

		var props = _.extend({}, this.props, {
			widgetType: validity.valid
				? (propValues.isRequired() ? "warning" : "info")
				: "danger",
			widgetTitle: propValues.getPropertyInfo().displayName,
			headerTitle: propValues.getPropertyInfo().comment
		});

		return propValues.getValuesType() == "dataProperty"
			? <DataPropertyWidget {...props} />
			: <ObjectPropertyWidget {...props} />;
	}

});

function getDataValueReplacer(backend, subject, predicate){

	return function(context, oldValue, newValue, onSuccess, onFailure){
		var successHandler = _.bind(onSuccess, context);
		var failureHandler = _.bind(onFailure, context);

		backend.performReplacement({
			subject: subject,
			predicate: predicate,
			oldObject: oldValue,
			newObject: newValue
		}).then(successHandler, failureHandler);
	};
}

module.exports = {
	Static: StaticWidget,
	Property: PropertyWidget
};

