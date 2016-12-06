
function filterByType(stationType, station){
	if (_.isEmpty(stationType)){
		return true;
	} else {
		return stationType[station.theme];
	}
}

function filterByStatus(appStatus, station){
	if (_.isEmpty(appStatus)){
		return true;
	} else {
		var filter = _.find(appStatus, function (filter) {
			return filter.value == station.hasApplicationStatus
		});

		return filter.selected;
	}
}

function filterByStationName(stationName, station){
	if (_.isEmpty(stationName)){
		return true;
	} else {
		return (station.hasLongName.toLowerCase().indexOf(stationName) > -1);
	}
}

export default function(StationsListStore, StationFilterStore){

	return Reflux.createStore({

		init: function(){
			this.listenTo(StationsListStore, this.stationsUpdateHandler);
			this.listenTo(StationFilterStore, this.filterStateHandler);

			this.filterState = StationFilterStore.getInitialState();
			this.stations = [];
		},

		filtersAreSatisfied: function(station) {
			return filterByType(this.filterState.stationType, station)
					&& filterByStatus(this.filterState.appStatus, station)
					&& filterByStationName(this.filterState.stationName, station);
		},

		filterStateHandler: function(filterState){
			this.filterState = filterState;
			this.filterStationsAndTrigger();
		},

		stationsUpdateHandler: function(stationsList){
			this.stations = stationsList.stations;
			this.filterStationsAndTrigger();
		},

		filterStationsAndTrigger: function(){
			this.trigger({
				stations: this.stations.filter(this.filtersAreSatisfied)
			});
		}
	});
}

