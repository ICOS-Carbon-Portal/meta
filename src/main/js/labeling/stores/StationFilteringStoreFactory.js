import {status} from '../models/ApplicationStatus.js';


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
			return filter.txt == station.hasApplicationStatus
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

export default function(StationsListStore, StationTypeFiltersAction, AppStatusFilterAction, StationNameFiltersAction){

	return Reflux.createStore({

		init: function(){
			this.listenTo(StationsListStore, this.stationsUpdateHandler);
			this.listenTo(StationTypeFiltersAction, this.filterByStationTypeHandler);
			this.listenTo(AppStatusFilterAction, this.filterByAppStatusHandler);
			this.listenTo(StationNameFiltersAction, this.filterByStationNameHandler);

			this.filterState = {
				stationType: localStorage.stationTypeFilters ? JSON.parse(localStorage.stationTypeFilters) : {},
				appStatus: localStorage.appStatusFilters ? JSON.parse(localStorage.appStatusFilters) : [],
				stationName: localStorage.stationNameFilter ? localStorage.stationNameFilter : ""
			};
			this.stations = [];
			//console.log(this.filterState.stationType);
		},

		filtersAreSatisfied: function(station) {
			return filterByType(this.filterState.stationType, station)
					&& filterByStatus(this.filterState.appStatus, station)
					&& filterByStationName(this.filterState.stationName, station);
		},

		stationsUpdateHandler: function(stationsList){
			this.stations = stationsList.stations;
			this.filterStationsAndTrigger();
		},

		filterByStationTypeHandler: function(stationTypeAction){
			localStorage.setItem('stationTypeFilters', JSON.stringify(stationTypeAction.stationTypeFilters));

			this.filterState.stationType = stationTypeAction.stationTypeFilters;
			this.filterStationsAndTrigger();
		},

		filterByAppStatusHandler: function(appStatusAction){
			localStorage.setItem('appStatusFilters', JSON.stringify(appStatusAction.appStatusFilters));

			this.filterState.appStatus = appStatusAction.appStatusFilters;
			this.filterStationsAndTrigger();
		},

		filterByStationNameHandler: function(stationNameAction){
			localStorage.setItem('stationNameFilter', stationNameAction.stationNameFilter);

			this.filterState.stationName = stationNameAction.stationNameFilter.toLowerCase();
			this.filterStationsAndTrigger();
		},

		filterStationsAndTrigger: function(){
			this.trigger({
				stations: this.stations.filter(this.filtersAreSatisfied)
			});
		}
	});
}