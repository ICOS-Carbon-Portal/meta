
function getValidatingMixin(validator){
	return {
		componentWillMount: function(){
			this.validators = this.validators || [];
			this.validators.push(validator);
		}
	};
}

var InputGroup = React.createClass({
	render: function() {
		return <div className="form-group">
			<label style={{width: 150, paddingRight: 4}}>{this.props.title}</label>
			{this.props.children}
		</div>;
	},
});

var InputBaseMixin = {
	componentWillMount: function(){
		this.validators = this.validators || [];
		this.inputStyle = {width: '80%'};
	},

	changeHandler: function(event){
		var newValue = this.extractUpdatedValue(event);
		var valid = _.every(this.validators, validator => validator(newValue));
		if(valid){
			this.props.updater(newValue);
		}
	}
};

var TextInputMixin = _.extend({
	render: function() {
		return <input type="text" onChange={this.changeHandler} value={this.props.value} style={this.inputStyle} />;
	}
}, InputBaseMixin);

var StringInputMixin = _.extend({
		extractUpdatedValue: event => event.target.value
	},
	TextInputMixin
);

var NumberInputMixin = _.extend({
		extractUpdatedValue: event => Number.parseFloat(event.target.value)
	},
	TextInputMixin
);

var IsNumberMixin = getValidatingMixin(value => !_.isNaN(value));


module.exports = function(saveStationAction) { 

	var NumberInput = React.createClass({mixins: [NumberInputMixin, IsNumberMixin]});
	var StringInput = React.createClass({mixins: [StringInputMixin]});

	return React.createClass({

		componentWillReceiveProps: function(newProps){
			this.setState(_.clone(newProps.station));
		},

		getInitialState: function(){
			return _.clone(this.props.station);
		},

		render: function() {
			var station = this.state;

			if (station.theme === 'Ecosystem')
				return <div>Station labeling support for Ecosystem stations is coming soon. Try the Atmosphere stations in the meanwhile.</div>;
			if (station.theme === 'Ocean')
				return <div>Station labeling support for Ocean stations is pending. Try the Atmosphere stations in the meanwhile.</div>;

			var isUnchanged = _.isEqual(station, this.props.station);

			var self = this;
			function updater(propName){
				return function(newValue){
					self.setState(_.object([propName], [newValue]));
				}
			}

			return <form role="form" onSubmit={this.submissionHandler}>
				<InputGroup title="Short name"><StringInput updater={updater("shortName")} value={station.shortName} /></InputGroup>
				<InputGroup title="Long name"><StringInput updater={updater("longName")} value={station.longName} /></InputGroup>
				<InputGroup title="Latitude"><NumberInput updater={updater("lat")} value={station.lat} /></InputGroup>
				<InputGroup title="Longitude"><NumberInput updater={updater("lon")} value={station.lon} /></InputGroup>
				<InputGroup title="Above ground"><StringInput updater={updater("aboveGround")} value={station.aboveGround} /></InputGroup>
				<InputGroup title="Above sea"><NumberInput updater={updater("aboveSea")} value={station.aboveSea} /></InputGroup>
				<InputGroup title="Station class"><NumberInput updater={updater("stationClass")} value={station.stationClass} /></InputGroup>
				<InputGroup title="Planned date starting"><StringInput updater={updater("plannedDateStarting")} value={station.plannedDateStarting} /></InputGroup>

				<button type="submit" className="btn btn-primary" disabled={isUnchanged}>Save</button>
			</form>;
		},

		submissionHandler: function(event) {
			event.preventDefault();
			saveStationAction(this.state);
		}

	});

}

