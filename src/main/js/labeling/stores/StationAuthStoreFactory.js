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

			this.state.stations = this.state.stations.map(station =>
				_.extend({}, station, {
					isUsersStation: _.contains(station.emails, this.whoami.mail)
				})
			);

			this.trigger(this.state);
		}
		
	});
	
	
}

