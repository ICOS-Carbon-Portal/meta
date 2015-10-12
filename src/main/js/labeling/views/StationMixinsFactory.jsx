module.exports = function(FileAwareStationStore, fileUploadAction, fileDeleteAction, saveStationAction, labelingStartAction) {

	var FileManager = require('./FileManagerFactory.jsx')(FileAwareStationStore, fileUploadAction, fileDeleteAction);
	var LabelingStartWidget = require('./LabelingStartWidgetFactory.jsx')(labelingStartAction);

	function stateFromStore(storeState){
		return {
			station: _.clone(storeState.chosen),
			originalStation: _.clone(storeState.chosen),
			errors: {},
			valid: true
		};
	}

	var InnerStore = Reflux.createStore({
		getInitialState: function(){
			return stateFromStore(FileAwareStationStore.getInitialState());
		},
		init: function(){
			this.listenTo(FileAwareStationStore, this.mapAndForward);
		},
		mapAndForward: function(fileAwareStoreState){
			this.trigger(stateFromStore(fileAwareStoreState));
		}
	});

	var StationBaseMixin = {
		render: function() {
			var station = this.state.station;
			if(!station || !station.stationUri || (station.stationUri !== this.props.stationUri)) return null;

			return <div>
				{this.getForm()}

				<FileManager />

				<LabelingStartWidget formIsValid={this.state.valid} station={this.state.station} isSaved={this.isUnchanged()} />

			</div>;
		},

		getUpdater: function(propName){
			var self = this;

			return (errors, newValue) => {
				var newState = _.clone(self.state);
				newState.errors = _.clone(self.state.errors);

				newState.errors[propName] = errors;
				newState.valid = _.isEmpty(_.flatten(_.values(newState.errors)));

				if(!_.isUndefined(newValue)){
					newState.station = _.clone(self.state.station);
					newState.station[propName] = newValue;
				}

				if(!_.isEqual(newState, self.state)) self.setState(newState);
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

		isUnchanged: function(){
			return _.isEqual(this.state.station, this.state.originalStation);
		},

		canSave: function(){
			return !this.isUnchanged() && this.state.valid && this.state.station.isUsersStation;
		},

		submissionHandler: function(event) {
			event.preventDefault();
			saveStationAction(this.state.station);
		}

	};

	return [StationBaseMixin, Reflux.connect(InnerStore)];

}

