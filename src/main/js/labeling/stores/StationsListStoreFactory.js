module.exports = function(Backend, chooseStationAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: _.constant({stations: []}),

		init: function(){
			this.state = this.getInitialState();
			this.listenTo(chooseStationAction, this.chooseStationHandler);

			var self = this;

			Backend.getStationPis().then(
				stations => {
					self.state.stations = stations;
					self.publishState();
				},
				err => console.log(err)
			);
		},

		chooseStationHandler: function(chosenStation) {
			var self = this;

			this.state.chosen = chosenStation;
			var chosenUri = chosenStation.stationUri;

			Backend.getProvisionalStationInfo(chosenUri).then(
				stationInfo => {
					if(this.state.chosen.stationUri !== chosenUri) return;

					self.state.stations = self.state.stations.map(stationInList => {
						var copy = _.clone(stationInList);
						copy.chosen = (stationInList.stationUri === chosenUri);
						return copy;
					});

					self.state.chosen = stationInfo;
					self.publishState();
				},
				err => console.log(err)
			);

		}

	});
}

