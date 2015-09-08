var Individual = require('../models/Individual.js');
var StaticWidget = require('./widgets/StaticWidget.jsx');
var PropertyWidget = require('./widgets/PropertyWidget.jsx');

module.exports = function(editStore, updateRequestAction){

	return React.createClass({

		mixins: [Reflux.connect(editStore)],

		render: function(){

			if(!this.state.individual) return <div></div>;

			var individ = new Individual(this.state.individual);

			function requestUpdate(updateRequest){
				var fullRequest = _.extend({}, updateRequest, {subject: individ.getInfo().uri});
				updateRequestAction(fullRequest);
			}

			return <div>
				<StaticWidget widgetTitle="Entry">{individ.getLabel()}</StaticWidget>
				<StaticWidget widgetTitle="Entry type">{individ.getClassInfo().displayName}</StaticWidget>{

					_.map(individ.getPropertyValues(), function(propValues, i){

						return <PropertyWidget key={propValues.getKey()} propertyValues={propValues} requestUpdate={requestUpdate}/>;

					})

				}
			</div>;
		}
	});
}

