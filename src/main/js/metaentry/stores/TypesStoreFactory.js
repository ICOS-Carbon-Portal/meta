module.exports = function(Backend, chooseTypeAction, checkSuffixAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: function(){
			return {
				types: [],
				chosen: null,
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
					self.state.types = _.chain(types)
						.map(function(theType){
							return _.extend({}, theType, {chosen: false});
						})
						.sortBy('displayName')
						.value();
					self.publishState();
				},
				function(err){console.log(err);}
			);
		},

		setChosenType: function(chosenType){

			if(this.state.chosen != chosenType){

				var newTypes = _.map(this.state.types, function(theType){
					return _.extend({}, theType, {chosen: (theType.uri == chosenType)});
				});

				this.state = {types: newTypes, chosen: chosenType};

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

			Backend.checkSuffix(baseClass, suffix).then(
				function(checkRes){
					if(baseClass !== self.state.chosen || suffix !== self.state.candidateUriSuffix) return;
					_.extend(self.state, checkRes);
					self.publishState();
				},
				function(err){console.log(err);}
			);
		}
	});
}

