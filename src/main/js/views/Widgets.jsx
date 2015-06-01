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
		return {value: this.props.dataValue};
	},
	render: function(){
		var content = this.state.value.getValue();
		var validity = this.state.value.getValidity();
		var style = validity.valid ? {} : {backgroundColor: "pink"};
		var title = validity.valid ? null : validity.errors.join('\n');

		return <input
			type="text" className="form-control" ref="dataValueInput"
			style={style} title={title}
			onKeyUp={this.changeHandler} value={content} onChange={this.changeHandler}
		/>;
	},
	changeHandler: function(){
		var elem = React.findDOMNode(this.refs.dataValueInput);
		var newValue = elem.value;
		var currentValue = this.state.value;
		if(currentValue.getValue() != newValue) {
			this.setState({value: currentValue.withValue(newValue)});
		}
	}
});

var DataPropertyWidget = React.createClass({
	render: function(){
		var propValues = this.props.propertyValues;

		return <Widget {...this.props}>{
			_.map(propValues.getValues(), function(value, i){
				var key = propValues.getKey() + "_val_" + i;
				return <DataValueWidget key={key} dataValue={value} />;
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

		var props = {
			propertyValues: propValues,
			widgetType: validity.valid
				? (propValues.isRequired() ? "warning" : "info")
				: "danger",
			widgetTitle: propValues.getPropertyInfo().displayName,
			headerTitle: propValues.getPropertyInfo().comment
		};

		return propValues.getValuesType() == "dataProperty"
			? <DataPropertyWidget {...props} />
			: <ObjectPropertyWidget {...props} />;
	}

});


module.exports = {
	Static: StaticWidget,
	Property: PropertyWidget
};

