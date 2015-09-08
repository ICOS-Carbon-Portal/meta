var DataPropertyWidget = require('./DataPropertyWidget.jsx');
var ObjectPropertyWidget = require('./ObjectPropertyWidget.jsx');

module.exports = React.createClass({

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

