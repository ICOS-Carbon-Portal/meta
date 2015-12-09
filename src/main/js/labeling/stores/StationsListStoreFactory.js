function unchoose(station){
	return station.chosen
			? _.extend({}, station, {chosen: false})
			: station;
}

export default function(Backend, chosenStationStore){
	return Reflux.createStore({

		publishStations: function(stations){
			this.stations = stations;
			this.trigger({stations: stations});
		},

		init: function(){
			this.listenTo(chosenStationStore, this.chosenStationHandler);

			Backend.getStationPis().then(
				_.bind(this.publishStations, this),
				err => console.log(err)
			);
		},

		chosenStationHandler: function(choice) {
			var chosen = choice.chosen;

			function updater(station){
				return station.stationUri === chosen.stationUri
					? _.extend({},
							station,
							{chosen: true},
							_.pick(chosen, 'hasApplicationStatus', 'hasLongName')
						)
					: unchoose(station);
			}

			if(chosen)
				this.publishStations(this.stations.map(updater));
			else
				this.publishStations(this.stations.map(unchoose));
		}

	});
}

