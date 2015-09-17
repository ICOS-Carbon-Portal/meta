var Widget = require('./Widget.jsx');

module.exports = React.createClass({

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

