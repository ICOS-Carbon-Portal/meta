module.exports = function(Backend, chooseStationAction){
	return Reflux.createStore({

		publishStations: function(stations){
			this.stations = stations;
			this.trigger({stations: stations});
		},

		init: function(){
			this.listenTo(chooseStationAction, this.chooseStationHandler);

			Backend.getStationPis().then(
				_.bind(this.publishStations, this),
				err => console.log(err)
			);
		},

		chooseStationHandler: function(chosenStation) {

			var stationUpdater = (chosenStation.chosen)
				//the original request comes from a mouse click on the station's panel heading,
				//and the station is already chosen. This means all stations need to be 'unchosen'
				? (station => station.chosen ? _.extend({}, station, {chosen: false}) : station)
				: (station => _.extend({}, station, {chosen: (station.stationUri === chosenStation.stationUri)}));

			this.publishStations(this.stations.map(stationUpdater));
		}

	});
}

