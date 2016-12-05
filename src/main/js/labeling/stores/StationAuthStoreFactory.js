import {status} from '../models/ApplicationStatus.js';

export default function(WhoAmIStore, StationsListStore){

	return Reflux.createStore({

		getInitialState: function(){
			return this.state;
		},

		init: function(){
			this.state = {stations: []};
			this.listenTo(WhoAmIStore, this.whoAmIHandler);
			this.listenTo(StationsListStore, this.stationListHandler);
		},

		whoAmIHandler: function(whoami){
			this.whoami = whoami;
			this.triggerIfReady();
		},

		stationListHandler: function(stationListState) {
			this.state = stationListState;
			this.triggerIfReady();
		},

		triggerIfReady: function(){

			if(!this.whoami || !this.state) return;
			var self = this;

			this.state = {
				stations: _.chain(this.state.stations)
					.map(station => self.decorateStation(station))
					.sortBy(station => {
						var isPi = station.isUsersStation ? 0 : 1;
						var isTc = station.isUsersTcStation ? 0 : 1;
						var attention = station.isUsersNeedingActionStation ? 0 : 1;
						return `${attention}${isPi}${isTc}_${station.theme}_${station.hasLongName}`;
					})
					.value()
			};

			this.trigger(this.state);
		},

		decorateStation: function(station){
			if(station) {
				let isUsersTcStation = _.contains(this.whoami.tcs, station.theme);
				let userIsPi = _.some(
					station.emails,
					piMail => piMail.toUpperCase() === this.whoami.mail.toUpperCase()
				);
				const isUsersDgStation = this.whoami.isDg;

				return _.extend({}, station, {
					isUsersStation: userIsPi,
					isUsersTcStation,
					isUsersDgStation,
					isUsersNeedingActionStation: isUsersTcStation && (
						station.hasApplicationStatus === status.submitted ||
						station.hasApplicationStatus === status.acknowledged
					)
				});
			} else return station;
		}

	});
	
	
}

