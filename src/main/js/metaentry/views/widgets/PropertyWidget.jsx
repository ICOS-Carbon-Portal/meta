export default {

	getWidgetProps: function(){
		var propValues = this.props.propertyValues;
		var validity = propValues.getValidity();
		var propInfo = propValues.getPropertyInfo();

		return {

			widgetType: validity.valid
				? (propValues.isRequired() ? "warning" : "light")
				: "danger",

			widgetTitle: propInfo.displayName,
			headerTitle: propInfo.comment,

			buttons: [{
				icon: 'plus',
				isDisabled: !propValues.canHaveMoreValues() || !this.newValueCanBeAdded(),
				clickHandler: _.bind(this.addNewValue, this)
			}]

		};
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
};

