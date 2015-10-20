import Widget from './Widget.jsx';
import PropertyWidget from './PropertyWidget.jsx';
import DataValueWidget from './DataValueWidget.jsx';

export default React.createClass({

	mixins: [PropertyWidget],

	render: function(){
		var self = this;

		return <Widget {...this.getWidgetProps()}>{
			_.map(this.props.propertyValues.getValues(), (value, i) =>
				<DataValueWidget key={'val_' + i} dataValue={value} requestUpdate={self.requestUpdate}/>
			)
		}</Widget>;

	},

	newValueCanBeAdded: _.constant(true)

});

