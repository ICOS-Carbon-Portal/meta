var Inputs = require('./FormInputs.jsx');

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
			var self = this;
			var station = this.state.station;

			var isUnchanged = _.isEqual(station, this.props.station);
			var cannotSave = isUnchanged || !this.state.valid || !station.isUsersStation;

			var updater = _.bind(this.getUpdater, this);

			function getProps(propName){
				return {
					updater: self.getUpdater(propName),
					value: station[propName],
					disabled: !station.isUsersStation
				};
			}

			var stationClassOptions = _.extend({
				options: {
					"1": "1",
					"2": "2",
					"3": "3",
					"Ass": "Ass"
				}
			}, getProps('stationClass'));

			return <form role="form" onSubmit={this.submissionHandler}>
				<Inputs.Group title="Short name"><Inputs.String {...getProps('shortName')} /></Inputs.Group>
				<Inputs.Group title="Long name"><Inputs.String {...getProps('longName')} /></Inputs.Group>
				<Inputs.Group title="Latitude"><Inputs.Latitude {...getProps('lat')} /></Inputs.Group>
				<Inputs.Group title="Longitude"><Inputs.Longitude {...getProps('lon')} /></Inputs.Group>
				<Inputs.Group title="Above ground"><Inputs.String {...getProps('aboveGround')} /></Inputs.Group>
				<Inputs.Group title="Above sea"><Inputs.Number {...getProps('aboveSea')} /></Inputs.Group>
				<Inputs.Group title="Station class"><Inputs.DropDownString {...stationClassOptions} /></Inputs.Group>
				<Inputs.Group title="Planned date starting"><Inputs.String {...getProps('plannedDateStarting')} /></Inputs.Group>

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

