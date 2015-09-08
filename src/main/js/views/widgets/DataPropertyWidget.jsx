var Widget = require('./Widget.jsx');
var DataValueWidget = require('./DataValueWidget.jsx');

module.exports = React.createClass({
	render: function(){
		var givenProps = this.props;
		var propValues = givenProps.propertyValues;

		return <Widget {...givenProps}>{
			_.map(propValues.getValues(), function(value, i){

				var props = _.extend({}, givenProps, {
					key: ["val", i, value.getValue()].join('_'),
					dataValue: value
				});

				return <DataValueWidget {...props}/>;
			})
		}</Widget>;

	}
});

