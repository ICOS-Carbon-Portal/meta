module.exports = function (Backend, ToasterStore, chooseStationAction, saveStationAction, updateStatusAction, updateStatusCommentAction) {
	return Reflux.createStore({

		init: function () {
			this.listenTo(chooseStationAction, this.chooseStationHandler);
			this.listenTo(saveStationAction, this.saveStationHandler);
			this.listenTo(updateStatusAction, this.updateStatusHandler);
			this.listenTo(updateStatusCommentAction, this.updateStatusCommentHandler);
		},

		chooseStationHandler: function (chosenStation, evenIfChosen) {
			if (chosenStation.chosen && !evenIfChosen) {
				this.chosen = null;
				this.trigger({ chosen: null });
				return;
			};

			this.chosen = chosenStation;
			var self = this;

			Backend.getStationInfo(chosenStation.stationUri, chosenStation.theme).then(
				stationInfo => {
					if (self.chosen.stationUri !== stationInfo.stationUri) return;

					var newChosenStation = _.extend({ chosen: true },
						_.pick(chosenStation, 'emails', 'isUsersStation', 'isUsersTcStation', 'isUsersDgStation', 'hasAppStatusComment'),
						stationInfo
					);
					self.trigger({ chosen: newChosenStation });
				},
				err => ToasterStore.showToasterHandler(err.message)
			);
		},

		refreshIfStillRelevant: function (station) {
			if (this.isStillRelevant(station)) this.chooseStationHandler(station, true);
		},

		isStillRelevant: function (station) {
			return this.chosen.stationUri === station.stationUri;
		},

		saveStationHandler: function (station) {
			var self = this;
			var stationInfo = _.omit(station, 'files', 'fileExpectations', 'fileTypes', 'emails',
				'chosen', 'isUsersStation', 'isUsersTcStation', 'isUsersDgStation', 'isUsersNeedingActionStation', 'hasApplicationStatus', 'hasStationClass');

			Backend.saveStationInfo(stationInfo).then(
				() => self.refreshIfStillRelevant(station),
				err => ToasterStore.showToasterHandler(err.message)
			);
		},

		updateStatusHandler: function (station) {
			var self = this;
			Backend.updateStatus(station.stationUri, station.hasApplicationStatus).then(
				() => {
					if (self.isStillRelevant(station)) self.trigger({ chosen: station });
				},
				err => ToasterStore.showToasterHandler(err.message)
			);
		},

		updateStatusCommentHandler: function (station) {
			Backend.updateStatusComment(station.stationUri, station.hasAppStatusComment).then(
				_ => _,
				err => ToasterStore.showToasterHandler(err.message)
			);
		}

	});
}

