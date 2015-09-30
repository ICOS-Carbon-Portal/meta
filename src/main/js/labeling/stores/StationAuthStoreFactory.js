module.exports = function(WhoAmIStore, StationsListStore){

	return Reflux.createStore({

		getInitialState: function(){
			return {stations: []};
		},

		init: function(){
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
								.sortBy(station => `${!station.isUsersStation}_${station.theme}_${station.longName}`)
								.value(),
				chosen: self.decorateStation(this.state.chosen)
			};

			this.trigger(this.state);
		},

		decorateStation: function(station){
			if(station) return _.extend({}, station, {
				isUsersStation: _.contains(station.emails, this.whoami.mail)
			});
		}

	});
	
	
}

