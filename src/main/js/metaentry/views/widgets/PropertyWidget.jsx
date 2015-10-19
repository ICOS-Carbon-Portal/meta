import DataPropertyWidget from './DataPropertyWidget.jsx';
import ObjectPropertyWidget from './ObjectPropertyWidget.jsx';

module.exports = React.createClass({

	render: function(){
		var self = this;
		var propValues = this.props.propertyValues;
		var validity = propValues.getValidity();
		var propInfo = propValues.getPropertyInfo();

		var props = _.extend(_.omit(this.props, "key"), {

			widgetType: validity.valid
				? (propValues.isRequired() ? "warning" : "info")
				: "danger",

			widgetTitle: propInfo.displayName,
			headerTitle: propInfo.comment,

			requestUpdate: _.bind(self.requestUpdate, self),

			buttons: [{
				glyphicon: 'plus',
				isDisabled: !propValues.canHaveMoreValues(),
				clickHandler: _.bind(self.addNewValue, self)
			}]

		});

		return propValues.getValuesType() === "dataProperty"
			? <DataPropertyWidget {...props} />
			: <ObjectPropertyWidget {...props} />;
	},

	requestUpdate: function(updateRequest){
		var predicate = this.props.propertyValues.getPropertyInfo().uri;
		var fullRequest = _.extend({}, updateRequest, {predicate: predicate});
		this.props.requestUpdate(fullRequest);
	},

	addNewValue: function(){
		this.requestUpdate({
			updates: [{isAssertion: true, obj: null}]
		});
	}

});

