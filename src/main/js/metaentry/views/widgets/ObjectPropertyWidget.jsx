import Widget from './Widget.jsx';
import PropertyWidget from './PropertyWidget.jsx';
import ObjectValueDropdown from './ObjectValueDropdown.jsx';

const ObjectValueWidget = React.createClass({
	render: function(){
		let style = this.props.orderNumber > 0 ? {marginTop: 3} : {};

		return <div className="input-group" style={style}>
			<a href={this.props.uri} className="input-group-text"><span className="fas fa-link"></span></a>
			<input type="text" className="form-control" readOnly value={this.props.label} />
			<button className="btn btn-outline-secondary" type="button" onClick={this.props.deleteSelf}>
				<span className="fas fa-times"></span>
			</button>
		</div>;
	}
});

export default React.createClass({

	mixins: [PropertyWidget],

	render: function(){
		var propValues = this.props.propertyValues;
		var keyBase = propValues.getKey()

		return <Widget {...this.getWidgetProps()}>
			<ObjectValueDropdown
				rangeValues={propValues.getRangeValues()}
				cancelAddition={this.cancelAddition}
				saveValue={this.saveValue}
			/>
			{
				_.map(propValues.getValues(), (value, i) =>
					<ObjectValueWidget
						key={keyBase + '_val_' + i}
						label={value.displayName}
						uri={value.uri}
						deleteSelf={this.getSelfDeleter(value.uri)}
						orderNumber={i}
					/>
				)
			}
		</Widget>;
	},

	newValueCanBeAdded: function(){
		//presence of the range values means we are already adding a new value right now;
		//the range values are temporarily injected by the EditStore, but removed when adding is done or canceled
		return !this.props.propertyValues.hasRangeValues();
	},

	saveValue: function(uri){
		this.requestUpdate({
			updates: [{isAssertion: true, obj: uri}]
		});
	},

	getSelfDeleter: function(uri){
		var self = this;
		return function(){
			self.requestUpdate({
				updates: [{isAssertion: false, obj: uri}]
			});
		};
	},

	cancelAddition: function(){
		this.requestUpdate({updates: []});
	}

});

