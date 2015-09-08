module.exports = function(Backend, chooseIndividAction, requestUpdateAction){
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
			this.listenTo(requestUpdateAction, this.processRequestUpdate);
		},

		fetchIndividual: function(individUri){
			var self = this;
			
			Backend.getIndividual(individUri)
				.then(function(individualInfo){
					self.state.individual = individualInfo;
					self.publishState();
				})
				.catch(function(err){console.log(err);});
		},

		processRequestUpdate: function(updateRequest){
			var self = this;
			if(updateRequest.type === "replace"){
				Backend.performReplacement(updateRequest)
					.then(function(){
						var individUri = self.state.individual.resource.uri;

						if(individUri === updateRequest.subject){
							self.fetchIndividual(individUri);
						}
					});
			}
		}

	});
}
