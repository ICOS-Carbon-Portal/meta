module.exports = function(chooseTypeAction, Backend){
	return Reflux.createStore({
		publishState: function(){
			this.trigger(this.state);
		},
		getInitialState: function(){
			return this.state;
		},
		init: function(){
			this.listenTo(chooseTypeAction, this.setChosenType);
			this.state = {
				types: [],
				chosen: null
			};
			var self = this;
			
			Backend.listClasses()
				.then(function(types){
					self.state.types = _.chain(types)
						.map(function(theType){
							return _.extend({}, theType, {chosen: false});
						})
						.sortBy('displayName')
						.value();
					self.publishState();
				})
				.catch(function(err){console.log(err);});
			
		},
		setChosenType: function(chosenType){

			if(this.state.chosen != chosenType){

				var newTypes = _.map(this.state.types, function(theType){
					return _.extend({}, theType, {chosen: (theType.uri == chosenType)});
				});

				this.state = {types: newTypes, chosen: chosenType};

				this.publishState();
			}

		}
	});
}
