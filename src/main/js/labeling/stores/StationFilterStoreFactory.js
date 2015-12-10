import {status} from '../models/ApplicationStatus.js';

function setDefaultFilters(){
	return {
		stationType: {
			Atmosphere: true,
			Ecosystem: true,
			Ocean: true
		},
		appStatus: _.map(status, function(txt, key){
			return {
				name: key,
				txt: txt,
				selected: true
			};
		}),
		stationName: ""
	};
}

export default function(Backend, stationFiltersAction){

	return Reflux.createStore({

		getInitialState: function(){
			return this.filterState;
		},

		init: function(){
			this.listenTo(stationFiltersAction, this.stationFiltersHandler);

			this.filterState = Backend.loadFilterState() || setDefaultFilters();
		},

		saveAndPublishFilterState: function(){
			Backend.saveFilterState(this.filterState);
			this.trigger(this.filterState);
		},

		stationFiltersHandler: function(filterAction){
			if (filterAction.clearAllFilters){
				this.filterState = setDefaultFilters();
			} else {
				_.extend(this.filterState, filterAction);
			}

			this.saveAndPublishFilterState();
		}

	});
}