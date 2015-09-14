module.exports = function(Backend, chooseTypeAction, chooseIndividAction, createIndividualAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: function(){
			return this.state;
		},

		init: function(){
			this.state = {
				individuals: [],
				addingInstance: false, //needed by the IndividualsList view to hide the IndividualAdder when refreshing
				chosen: null
			};
			this.listenTo(chooseTypeAction, this.fetchIndividuals);
			this.listenTo(chooseIndividAction, this.updateChosenIndivid);
			this.listenTo(createIndividualAction, this.createIndividual);
		},

		fetchIndividuals: function(chosenType){

			var self = this;
			self.chosenType = chosenType;

			Backend.listIndividuals(chosenType).then(
				function(individuals){

					if(chosenType !== self.chosenType) return;

					self.state.individuals = individuals;
					self.publishState();
				},
				function(err){
					self.chosenType = undefined;
					console.log(err);
				}
			);
		},

		updateChosenIndivid: function(chosenIndivid){
			this.state.chosen = chosenIndivid;
			this.publishState();
		},

		createIndividual: function(newIndReq){
			var self = this;
			if(this.chosenType !== newIndReq.type) return;

			Backend.createIndividual(newIndReq.uri, newIndReq.type).then(
				function(){
					self.state.individuals = self.state.individuals.concat([{
						displayName: newIndReq.suffix,
						uri: newIndReq.uri
					}]);
					chooseIndividAction(newIndReq.uri); //will trigger both this store and the EditStore
				},
				function(err){console.log(err);}
			);
		}

	});
}

