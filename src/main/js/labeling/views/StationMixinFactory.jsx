var Inputs = require('./FormInputs.jsx');

module.exports = function(FileManager, saveStationAction, labelingStartAction) {

	var LabelingStartWidget = require('./LabelingStartWidgetFactory.jsx')(labelingStartAction);

	function stateFromProps(props){
		return {
			station: _.clone(props.station),
			errors: {},
			valid: true
		};
	}

	return {
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

			return <div>
				{this.getForm()}

				<FileManager station={this.state.station} />

				<LabelingStartWidget formIsValid={this.state.valid} station={this.state.station} isSaved={isUnchanged} />

			</div>;
		},

		getUpdater: function(propName){
			var self = this;

			return (errors, newValue) => {
				var newState = _.clone(self.state);
				newState.station = _.clone(self.state.station);
				newState.errors = _.clone(self.state.errors);

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

		getProps: function(propName){
			var station = this.state.station;
			return {
				updater: this.getUpdater(propName),
				value: station[propName],
				disabled: !station.isUsersStation
			};
		},

		canSave: function(){
			var station = this.state.station;
			var isChanged = !_.isEqual(station, this.props.station);
			return isChanged && this.state.valid && station.isUsersStation;
		},

		submissionHandler: function(event) {
			event.preventDefault();
			saveStationAction(this.state.station);
		}

	};

}

