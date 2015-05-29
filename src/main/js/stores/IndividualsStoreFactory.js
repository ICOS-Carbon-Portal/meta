module.exports = function(Backend, chooseTypeAction, chooseIndividAction){
	return Reflux.createStore({
	
		publishState: function(){
			this.trigger(this.state);
		},
		
		getInitialState: function(){
			return this.state;
		},
		
		init: function(){
			this.listenTo(chooseTypeAction, this.fetchIndividuals);
			this.listenTo(chooseIndividAction, this.updateChosenIndivid);
			this.state = {
				individuals: [],
				chosen: null
			};
		},
		
		fetchIndividuals: function(chosenType){

			var self = this;
			
			Backend.listIndividuals(chosenType)
				.then(function(individuals){
					self.state.individuals = individuals;
					self.publishState();
				})
				.catch(function(err){console.log(err);});
		},
		
		updateChosenIndivid: function(chosenIndivid){
			this.state.chosen = chosenIndivid;
			this.publishState();
		}

	});
}
