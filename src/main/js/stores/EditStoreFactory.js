module.exports = function(Backend, chooseIndividAction){
	return Reflux.createStore({
	
		publishState: function(){
			this.trigger(this.state);
		},
		
		getInitialState: function(){
			return this.state;
		},
		
		init: function(){
			this.state = {individual: null};
			this.listenTo(chooseIndividAction, this.fetchIndividual);
		},
		
		fetchIndividual: function(individUri){
			//don't do anything if the chosen is the same
			if(this.state.individual && (this.state.individual.resource.uri == individUri)) return;
			
			var self = this;
			
			Backend.getIndividual(individUri)
				.then(function(individualInfo){
					self.state.individual = individualInfo;
					self.publishState();
				})
				.catch(function(err){console.log(err);});
		}

	});
}
