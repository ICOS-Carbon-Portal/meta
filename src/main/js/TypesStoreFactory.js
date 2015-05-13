module.exports = function(chooseTypeAction){
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
				types: [
					{displayName: "Station", uri: "station", chosen: false},
					{displayName: "Thematic Center", uri: "center", chosen: false},
					{displayName: "Person", uri: "person", chosen: false}
				],
				chosen: null
			};
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