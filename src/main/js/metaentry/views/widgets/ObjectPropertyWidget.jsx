import Widget from './Widget.jsx';
import PropertyWidget from './PropertyWidget.jsx';
import ObjectValueDropdown from './ObjectValueDropdown.jsx';

const ObjectValueWidget = React.createClass({
	render: function(){
		return <div className="input-group">
			<input type="text" className="form-control" readOnly value={this.props.label} />
			<span className="input-group-btn">
				<button className="btn btn-default" type="button" onClick={this.props.deleteSelf}>
					<span className="glyphicon glyphicon-remove"></span>
				</button>
			</span>
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
						deleteSelf={this.getSelfDeleter(value.uri)}
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

