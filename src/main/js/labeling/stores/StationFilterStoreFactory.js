import {status} from '../models/ApplicationStatus.js';

function setDefaultFilters(deselectAllAppStatuses){
	return {
		stationType: {
			Atmosphere: true,
			Ecosystem: true,
			Ocean: true
		},
		appStatus: _.map(status, function(value, key){
			return {
				value,
				selected: !deselectAllAppStatuses
			};
		}),
		stationName: ""
	};
}

export default function(stationFiltersAction){

	return Reflux.createStore({

		getInitialState: function(){
			return this.filterState;
		},

		init: function(){
			this.listenTo(stationFiltersAction, this.stationFiltersHandler);
			this.filterState = setDefaultFilters();
		},

		stationFiltersHandler: function(filterAction){

			if (filterAction.resetAllFilters)
				this.filterState = setDefaultFilters();
			else
				if(filterAction.unselectStatuses)
					this.filterState = setDefaultFilters(true);
			else
				_.extend(this.filterState, filterAction);

			this.trigger(this.filterState);
		}

	});
}
