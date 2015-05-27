module.exports = function(Backend, chooseIndividAction){
	return Reflux.createStore({
	
		publishState: function(){
			this.trigger(this.state);
		},
		
		getInitialState: function(){
			return this.state;
		},
		
		init: function(){
			this.listenTo(chooseIndividAction, this.fetchIndividual);
			this.state = {
				individual: null
			};
		},
		
		fetchIndividual: function(individual){
			//don't do anything if the chosen is the same
			if(this.state.individual && this.state.individual.resource.uri == individual.uri) return;
			
			var self = this;
			
			Backend.getIndividual(individual.uri)
				.then(function(individualInfo){
					self.state.individual = individualInfo;
					self.publishState();
				})
				.catch(function(err){console.log(err);});
		}

	});
}
