module.exports = function(Backend, chooseStationAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: _.constant({stations: []}),

		init: function(){
			this.state = this.getInitialState();
			this.listenTo(chooseStationAction, this.setChosenStation);
			var self = this;

			Backend.getStationPis().then(
				stations => {
					self.state.stations = _.chain(stations)
						.groupBy('stationUri')
						.values()
						.map(samePi => _.extend(
								_.omit(samePi[0], 'email'), {
									emails: _.pluck(samePi, 'email'),
									chosen: false
								}
							)
						)
						.sortBy('longName')
						.value();

					self.publishState();
				},
				err => console.log(err)
			);
		},

		setChosenStation: function(chosenStation){

			if(chosenStation.chosen) return;

			this.state.stations = _.map(this.state.stations, theStation =>
				_.extend({}, theStation, {chosen: (theStation.stationUri == chosenStation.stationUri)})
			);

			this.publishState();
		}

	});
}

