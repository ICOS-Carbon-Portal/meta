var Individual = require('../models/Individual.js');
var StaticWidget = require('./widgets/StaticWidget.jsx');
var PropertyWidget = require('./widgets/PropertyWidget.jsx');
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

module.exports = function(editStore, updateRequestAction){

	return React.createClass({

		mixins: [Reflux.connect(editStore)],

		render: function(){

			if(!this.state.individual) return null;

			var individ = new Individual(this.state.individual);
			var indKey = individ.getKey() + '_';

			function requestUpdate(updateRequest){
				var fullRequest = _.extend({}, updateRequest, {subject: individ.getInfo().uri});
				updateRequestAction(fullRequest);
			}

			return <Widget widgetType="primary" widgetTitle="Entry editor">
				<ScreenHeightColumn>
					<StaticWidget widgetTitle="Entry">{individ.getLabel()}</StaticWidget>
					<StaticWidget widgetTitle="Entry type">{individ.getClassInfo().displayName}</StaticWidget>{

						_.map(individ.getPropertyValues(), function(propValues, i){

							return <PropertyWidget key={indKey + propValues.getKey()} propertyValues={propValues} requestUpdate={requestUpdate}/>;

						})

					}
				</ScreenHeightColumn>
			</Widget>;
		}
	});
}

