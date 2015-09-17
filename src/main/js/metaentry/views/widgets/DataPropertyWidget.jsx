var Widget = require('./Widget.jsx');
var DataValueWidget = require('./DataValueWidget.jsx');

module.exports = React.createClass({
	render: function(){
		var givenProps = this.props;
		var propValues = givenProps.propertyValues;

		var addNewValue = _.bind(this.props.requestUpdate, null, {
			updates: [{
				isAssertion: true,
				obj: null
			}]
		});

		var widgetProps = _.extend({}, givenProps, {
			buttons: [{
				glyphicon: 'plus',
				isDisabled: !propValues.canHaveMoreValues(),
				clickHandler: addNewValue
			}]
		});

		return <Widget {...widgetProps}>{
			_.map(propValues.getValues(), function(value, i){

				var props = _.extend({}, givenProps, {
					key: "val_" + i,
					dataValue: value
				});

				return <DataValueWidget {...props}/>;
			})
		}</Widget>;

	}
});

