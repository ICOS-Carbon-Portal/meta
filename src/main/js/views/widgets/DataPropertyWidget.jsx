var Widget = require('./Widget.jsx');
var DataValueWidget = require('./DataValueWidget.jsx');

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

module.exports = React.createClass({
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

