module.exports = function(Backend, chooseObjectAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: _.constant({stations: []}),

		init: function(){
			this.state = this.getInitialState();
			
			this.listenTo(chooseObjectAction, this.doChooseObjectAction);
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
                     
		doChooseObjectAction: function(object) {
			
			this.state.stations = this.state.stations.map(function(stationInList) {
				var station = _.clone(stationInList);
				
				station.chosen = (station.stationUri === object.stationUri);
				
				return station;
			});
			
			this.publishState();
		}

	});
}

