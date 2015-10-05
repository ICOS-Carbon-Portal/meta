module.exports = function(Backend, FileUploadStore, chooseStationAction, saveStationAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: _.constant({stations: []}),

		init: function(){
			this.state = this.getInitialState();
			this.listenTo(chooseStationAction, this.chooseStationHandler);
			this.listenTo(saveStationAction, this.saveStationHandler);
			this.listenTo(FileUploadStore, this.fileUpdateHandler);

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

			if(chosenStation.chosen) {
				//the original request comes from a mouse click on the station's panel heading,
				//and the station is already chosen. This means the station needs to be 'unchosen'
				this.state.chosen = undefined;
				this.state.stations = self.state.stations.map(station =>
					station.chosen ? _.extend({}, station, {chosen: false}) : station
				);
				this.publishState();
				return;
			}

			this.state.chosen = chosenStation;
			var chosenUri = chosenStation.stationUri;

			Backend.getStationInfo(chosenUri).then(
				stationInfo => {
					if(this.state.chosen.stationUri !== chosenUri) return;

					self.state.stations = self.state.stations.map(stationInList => {
						var copy = _.clone(stationInList);
						copy.chosen = (stationInList.stationUri === chosenUri);
						return copy;
					});

					self.state.chosen = _.extend({emails: chosenStation.emails}, stationInfo);
					self.publishState();
				},
				err => console.log(err)
			);
		},

		saveStationHandler: function(stationInfo){
			var self = this;
			Backend.saveStationInfo(stationInfo).then(
				() => self.chooseStationHandler(stationInfo),
				err => console.log(err)
			);
		},

		fileUpdateHandler: function(fileInfo){

			if(!this.state || !this.state.chosen) return;
			if(this.state.chosen.stationUri !== fileInfo.stationUri) return;

			this.chooseStationHandler(this.state.chosen);
		}

	});
}

