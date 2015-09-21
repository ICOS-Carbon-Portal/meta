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
		    this.stations = stationListState.stations;
			this.triggerIfReady();
	    },

		triggerIfReady: function(){
			
			if(!this.whoami || !this.stations) return;

			var stations = this.stations.map(station => _.extend({}, station, {
					isUsersStation: _.contains(station.emails, this.whoami.email)
				})
			);
		    
		    this.trigger({stations: stations});
			
		}
		
	});
	
	
}
