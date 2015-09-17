var DataPropertyWidget = require('./DataPropertyWidget.jsx');
var ObjectPropertyWidget = require('./ObjectPropertyWidget.jsx');

module.exports = React.createClass({

	render: function(){
		var givenProps = this.props;
		var propValues = givenProps.propertyValues;
		var validity = propValues.getValidity();
		var propInfo = propValues.getPropertyInfo();

		var props = _.extend(_.omit(givenProps, "key"), {

			widgetType: validity.valid
				? (propValues.isRequired() ? "warning" : "info")
				: "danger",

			widgetTitle: propInfo.displayName,

			headerTitle: propInfo.comment,

			requestUpdate: function(updateRequest){
				var fullRequest = _.extend({}, updateRequest, {predicate: propInfo.uri});
				givenProps.requestUpdate(fullRequest);
			}

		});

		return propValues.getValuesType() == "dataProperty"
			? <DataPropertyWidget {...props} />
			: <ObjectPropertyWidget {...props} />;
	}

});

