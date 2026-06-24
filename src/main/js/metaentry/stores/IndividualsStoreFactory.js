var urlManager = require('../urlManager.js');

module.exports = function(Backend, selectTypeAction, selectIndividAction, selectIndividualByPathAction, createIndividualAction, deleteIndividualAction, initialIndividualName){
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
				individualsSparql: null,
				selectedType: null,
				addingInstance: false, //needed by the IndividualsList view to hide the IndividualAdder when refreshing
				selectedIndividual: null,
				loadingIndividuals: false
			};
			this.pendingIndividualName = initialIndividualName || null;
			this.listenTo(selectTypeAction, this.fetchIndividuals);
			this.listenTo(selectIndividAction, this.updateSelectedIndivid);
			this.listenTo(selectIndividualByPathAction, this.selectIndividualByPath);
			this.listenTo(createIndividualAction, this.createIndividual);
			this.listenTo(deleteIndividualAction, this.deleteIndividual);
		},

		selectIndividualByPath: function(individualName){
			this.pendingIndividualName = individualName || null;
			if(!individualName) {
				selectIndividAction(null);
				return;
			}
			var match = urlManager.findByPathName(this.state.individuals, individualName);
			if(match) {
				this.pendingIndividualName = null;
				selectIndividAction(match.uri);
			}
		},

		fetchIndividuals: function(selectedType){

			var self = this;
			self.selectedType = selectedType;
			self.state.selectedType = selectedType;
			self.state.selectedIndividual = null;
			self.state.individuals = [];
			self.state.individualsSparql = null;
			self.state.loadingIndividuals = !!selectedType;
			selectIndividAction(null);
			self.publishState();

			if(!selectedType) return;

			Backend.listIndividuals(selectedType).then(
				function(individuals){

					// Ignore stale async responses after the user switched type.
					if(selectedType !== self.selectedType) return;

					self.state.individuals = individuals;
					self.state.loadingIndividuals = false;

					if(self.pendingIndividualName){
						var match = urlManager.findByPathName(individuals, self.pendingIndividualName);
						self.pendingIndividualName = null;
						if(match) selectIndividAction(match.uri);
					}

					self.publishState();
				},
				function(err){
					if(selectedType === self.selectedType) {
						self.selectedType = undefined;
						self.state.loadingIndividuals = false;
						self.publishState();
					}
					console.log(err);
				}
			);

			Backend.getIndividualsSparql(selectedType).then(
				function(individualsSparql){

					// Ignore stale async responses after the user switched type.
					if(selectedType !== self.selectedType) return;

					self.state.individualsSparql = individualsSparql;
					self.publishState();
				},
				function(err){
					console.log(err);
				}
			);
		},

		updateSelectedIndivid: function(selectedIndivid){
			this.state.selectedIndividual = selectedIndivid;
			this.publishState();
		},

		createIndividual: function(newIndReq){
			var self = this;
			if(this.selectedType !== newIndReq.type) return;

			Backend.createIndividual(newIndReq.uri, newIndReq.type).then(
				function(){
					//TODO Revise the next two lines and the related data flow
					self.fetchIndividuals(newIndReq.type);
					selectIndividAction(newIndReq.uri); //will trigger both this store and the EditStore
				},
				function(err){console.log(err);}
			);
		},

		deleteIndividual: function(indUri){
			var self = this;
			Backend.deleteIndividual(indUri).then(
				function(){
					self.state.individuals = _.filter(self.state.individuals, function(ind){
						return ind.uri !== indUri;
					});
					//TODO Revise the next lines and the related data flow
					if(self.state.selectedIndividual === indUri) {
						self.state.selectedIndividual = null;
						selectIndividAction(null);
					} else self.publishState();
					
				},
				function(err){console.log(err);}
			);
		}

	});
}
