
function getValidatingMixin(){
	var validators = arguments;

	return {
		componentWillMount: function(){
			var self = this;
			self.validators = self.validators || [];
			_.each(validators, validator => self.validators.push(validator));
		}
	};
}

var InputGroup = React.createClass({
	render: function() {
		return <div className="form-group">
			<label style={{width: 150, paddingRight: 4}}>{this.props.title}</label>
			{this.props.children}
		</div>;
	}
});

var InputBaseMixin = {

	componentWillMount: function(){
		this.validators = this.validators || [];
		this.inputStyle = {width: '80%'};
	},

	getErrors: function(value){
		var firstFailing = _.find(this.validators, validator => !_.isEmpty(validator(value)));
		return firstFailing ? firstFailing(value) : [];
	},

	changeHandler: function(event){
		var newValue = event.target.value;
		var errors = this.getErrors(newValue);
		var finalValue = _.isEmpty(errors) ? this.extractUpdatedValue(newValue) : newValue;
		this.props.updater(errors, finalValue);
	}
};

var TextInputMixin = _.extend({
	render: function() {
		var errors = this.getErrors(this.props.value);

		var style = _.extend(_.isEmpty(errors) ? {} : {backgroundColor: "pink"}, this.inputStyle);

		return <input type="text" onChange={this.changeHandler} title={errors.join('\n')} value={this.props.value} style={style} />;
	}
}, InputBaseMixin);

var StringInputMixin = _.extend({
		extractUpdatedValue: _.identity
	},
	TextInputMixin
);

var NumberInputMixin = _.extend({
		extractUpdatedValue: s => Number.parseFloat(s)
	},
	TextInputMixin
);

var IsNumberMixin = getValidatingMixin(value => {
	return (Number.parseFloat(value).toString() === value.toString()) ? [] : ["Not a valid number!"];
});

function fromMixins(){
	var mixins = [];
	_.each(arguments, argument => mixins.push(argument));
	return React.createClass({mixins: mixins});
}


var NumberInput = fromMixins(NumberInputMixin, IsNumberMixin);
var StringInput = fromMixins(StringInputMixin);

module.exports = function(saveStationAction) {

	function stateFromProps(props){
		return {
			station: _.clone(props.station),
			errors: {},
			valid: true
		};
	}

	return React.createClass({

		componentWillReceiveProps: function(newProps){
			this.setState(stateFromProps(newProps));
		},

		getInitialState: function(){
			return stateFromProps(this.props);
		},

		render: function() {
			var station = this.state.station;

			if (station.theme === 'Ecosystem')
				return <div>Station labeling support for Ecosystem stations is coming soon. Try the Atmosphere stations in the meanwhile.</div>;
			if (station.theme === 'Ocean')
				return <div>Station labeling support for Ocean stations is pending. Try the Atmosphere stations in the meanwhile.</div>;

			var isUnchanged = _.isEqual(station, this.props.station);
			var cannotSave = isUnchanged || !this.state.valid;

			var updater = _.bind(this.getUpdater, this);

			return <form role="form" onSubmit={this.submissionHandler}>
				<InputGroup title="Short name"><StringInput updater={updater("shortName")} value={station.shortName} /></InputGroup>
				<InputGroup title="Long name"><StringInput updater={updater("longName")} value={station.longName} /></InputGroup>
				<InputGroup title="Latitude"><NumberInput updater={updater("lat")} value={station.lat} /></InputGroup>
				<InputGroup title="Longitude"><NumberInput updater={updater("lon")} value={station.lon} /></InputGroup>
				<InputGroup title="Above ground"><StringInput updater={updater("aboveGround")} value={station.aboveGround} /></InputGroup>
				<InputGroup title="Above sea"><NumberInput updater={updater("aboveSea")} value={station.aboveSea} /></InputGroup>
				<InputGroup title="Station class"><NumberInput updater={updater("stationClass")} value={station.stationClass} /></InputGroup>
				<InputGroup title="Planned date starting"><StringInput updater={updater("plannedDateStarting")} value={station.plannedDateStarting} /></InputGroup>

				<button type="submit" className="btn btn-primary" disabled={cannotSave}>Save</button>
			</form>;
		},

		getUpdater: function(propName){
			var self = this;

			return (errors, newValue) => {
				var newState = _.clone(self.state);
				newState.station[propName] = newValue;

				if(_.isEmpty(errors)){
					newState.errors[propName] = [];
					newState.valid = _.isEmpty(_.flatten(_.values(newState.errors)));
				} else{
					newState.errors[propName] = errors;
					newState.valid = false;
				}

				self.setState(newState);
			};
		},

		submissionHandler: function(event) {
			event.preventDefault();
			saveStationAction(this.state.station);
		}

	});

}

