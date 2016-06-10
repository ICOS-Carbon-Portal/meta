import StaticWidget from './widgets/StaticWidget.jsx';
import DataPropertyWidget from './widgets/DataPropertyWidget.jsx';
import ObjectPropertyWidget from './widgets/ObjectPropertyWidget.jsx';
import Widget from './widgets/Widget.jsx';
import ScreenHeightColumn from './ScreenHeightColumn.jsx';

export default function(editStore, updateRequestAction){

	return React.createClass({

		mixins: [Reflux.connect(editStore)],

		render: function(){

			if(!this.state.individual) return null;

			var individ = this.state.individual;
			var indKey = individ.getKey() + '_';
			const uri = individ.getInfo().uri;

			function requestUpdate(updateRequest){
				var fullRequest = _.extend({}, updateRequest, {subject: individ.getInfo().uri});
				updateRequestAction(fullRequest);
			}

			return <Widget widgetType="primary" widgetTitle="Entry editor">
				<ScreenHeightColumn>
					<StaticWidget widgetTitle="Entry">{individ.getLabel()} (<a href={uri} target="_blank">{uri}</a>)</StaticWidget>
					<StaticWidget widgetTitle="Entry type">{individ.getClassInfo().displayName}</StaticWidget>{

						_.map(individ.getPropertyValues(), function(propValues, i){

							let PropertyWidget = propValues.getValuesType() === "dataProperty"
								? DataPropertyWidget
								: ObjectPropertyWidget;

							return <PropertyWidget key={indKey + propValues.getKey()} propertyValues={propValues} requestUpdate={requestUpdate}/>;

						})

					}
				</ScreenHeightColumn>
			</Widget>;
		}
	});
}

