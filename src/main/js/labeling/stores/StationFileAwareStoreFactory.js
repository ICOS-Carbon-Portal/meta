module.exports = function(StationAuthStore){

	return Reflux.createStore({

		getInitialState: function(){
			return StationAuthStore.getInitialState();
		},

		init: function(){
			this.listenTo(StationAuthStore, this.stationListHandler);
		},

		stationListHandler: function(state) {
			var newState = _.clone(state);
			newState.chosen = this.addFileTypes(state.chosen);
			this.trigger(newState);
		},

		addFileTypes: function(station){

			if(!station) return station;

			return _.extend({
				fileTypes: ['Basic information', 'Satellite image']
			}, station);
		}

	});
	
	
}

