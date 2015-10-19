var Widget = require('./Widget.jsx');
var DataValueWidget = require('./DataValueWidget.jsx');

module.exports = React.createClass({
	render: function(){
		var self = this;

		return <Widget {...self.props}>{
			_.map(self.props.propertyValues.getValues(), function(value, i){

				var props = _.extend({}, self.props, {
					key: "val_" + i,
					dataValue: value
				});

				return <DataValueWidget {...props}/>;
			})
		}</Widget>;

	}
});

