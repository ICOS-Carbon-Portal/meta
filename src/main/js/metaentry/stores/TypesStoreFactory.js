export default function(Backend, chooseTypeAction, checkSuffixAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: function(){
			return {
				types: [],
				chosen: null,
				chosenIdx: -1,
				candidateUriSuffix: null,
				candidateUri: null,
				suffixAvailable: false
			};
		},

		init: function(){
			this.listenTo(chooseTypeAction, this.setChosenType);
			this.listenTo(checkSuffixAction, this.checkSuffix);
			this.state = this.getInitialState();
			var self = this;

			Backend.listClasses().then(
				function(types){
					self.state.types = _.sortBy(types, 'displayName');
					self.publishState();
				},
				function(err){console.log(err);}
			);
		},

		setChosenType: function(chosenType){

			if(this.state.chosen != chosenType){

				const chosenIdx = _.findIndex(this.state.types, theType => (theType.uri == chosenType));

				this.state = _.extend(this.getInitialState(), {
					types: this.state.types,
					chosen: chosenType,
					chosenIdx
				});

				this.publishState();
			}

		},

		checkSuffix: function(suffix){
			var self = this;
			var baseClass = this.state.chosen;

			_.extend(this.state, {
				candidateUriSuffix: suffix,
				candidateUri: null,
				suffixAvailable: false
			});

			if(!baseClass || !suffix) {
				self.publishState();
				return;
			}

			const uriBase = this.state.types[this.state.chosenIdx].newInstanceBaseUri;

			Backend.checkSuffix(uriBase, suffix).then(
				function(checkRes){
					if(baseClass === self.state.chosen && suffix === self.state.candidateUriSuffix){
						_.extend(self.state, checkRes);
						self.publishState();
					}
				},
				function(err){console.log(err);}
			);
		}
	});
}

