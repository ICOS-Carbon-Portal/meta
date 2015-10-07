module.exports = function(Backend, chooseStationAction, saveStationAction){
	return Reflux.createStore({

		init: function(){
			this.listenTo(chooseStationAction, this.chooseStationHandler);
			this.listenTo(saveStationAction, this.saveStationHandler);
		},

		chooseStationHandler: function(chosenStation, evenIfChosen) {
			if(chosenStation.chosen && !evenIfChosen) return;

			this.chosen = chosenStation;
			var self = this;

			Backend.getStationInfo(chosenStation.stationUri).then(
				stationInfo => {
					if(self.chosen.stationUri !== stationInfo.stationUri) return;

					var newChosenStation = _.extend({chosen: true},
						_.pick(chosenStation, 'emails', 'isUsersStation'),
						stationInfo
					);

					self.trigger({chosen: newChosenStation});
				},
				err => console.log(err)
			);
		},

		saveStationHandler: function(stationInfo){
			var self = this;
			Backend.saveStationInfo(stationInfo).then(
				() => {
					if(self.chosen.stationUri === stationInfo.stationUri){
						self.chooseStationHandler(stationInfo, true);
					}
				},
				err => console.log(err)
			);
		}

	});
}

