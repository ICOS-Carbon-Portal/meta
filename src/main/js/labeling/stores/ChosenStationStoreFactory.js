module.exports = function(Backend, chooseStationAction, saveStationAction, updateStatusAction){
	return Reflux.createStore({

		init: function(){
			this.listenTo(chooseStationAction, this.chooseStationHandler);
			this.listenTo(saveStationAction, this.saveStationHandler);
			this.listenTo(updateStatusAction, this.updateStatusHandler);
		},

		chooseStationHandler: function(chosenStation, evenIfChosen) {
			if(chosenStation.chosen && !evenIfChosen) {
				this.chosen = null;
				this.trigger({chosen: null});
				return;
			};

			this.chosen = chosenStation;
			var self = this;

			Backend.getStationInfo(chosenStation.stationUri).then(
				stationInfo => {
					if(self.chosen.stationUri !== stationInfo.stationUri) return;

					var newChosenStation = _.extend({chosen: true},
						_.pick(chosenStation, 'emails', 'isUsersStation', 'isUsersTcStation'),
						stationInfo
					);
					self.trigger({chosen: newChosenStation});
				},
				err => console.log(err)
			);
		},

		refreshIfStillRelevant: function(station){
			if(this.isStillRelevant(station)) this.chooseStationHandler(station, true);
		},

		isStillRelevant: function(station){
			return this.chosen.stationUri === station.stationUri;
		},

		saveStationHandler: function(station){
			var self = this;
			var stationInfo = _.omit(station, 'files', 'fileExpectations', 'fileTypes', 'emails',
				'chosen', 'isUsersStation', 'isUsersTcStation', 'isUsersNeedingActionStation', 'hasApplicationStatus');

			Backend.saveStationInfo(stationInfo).then(
				() => self.refreshIfStillRelevant(station),
				err => console.log(err)
			);
		},

		updateStatusHandler: function(station){
			var self = this;
			Backend.updateStatus(station.stationUri, station.hasApplicationStatus).then(
				() => {
					if(self.isStillRelevant(station)) self.trigger({chosen: station});
				},
				err => console.log(err)
			);
		}

	});
}

