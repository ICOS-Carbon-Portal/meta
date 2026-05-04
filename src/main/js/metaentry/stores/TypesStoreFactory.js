export default function(Backend, selectTypeAction, checkUriOrSuffixAction){

	const uriCheck = _.debounce(
		function(uri, onSuccess){
			Backend.checkUri(uri).then(
				onSuccess,
				function(err){console.log(err)}
			)
		},
		200
	)

	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},

		getInitialState: function(){
			return {
				types: [],
				selected: null,
				selectedIdx: -1,
				candidateUri: null,
				uriAvailable: false
			};
		},

		init: function(){
			this.listenTo(selectTypeAction, this.setSelectedType);
			this.listenTo(checkUriOrSuffixAction, this.checkUriOrSuffix);
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

		setSelectedType: function(selectedType){

			if(this.state.selected != selectedType){

				const selectedIdx = _.findIndex(this.state.types, theType => (theType.uri == selectedType));

				this.state = _.extend(this.getInitialState(), {
					types: this.state.types,
					selected: selectedType,
					selectedIdx
				});

				this.publishState();
			}

		},

		checkUriOrSuffix: function(uriOrSuffix){
			if(!uriOrSuffix || this.state.selectedIdx < 0 || this.state.selectedIdx >= this.state.types.length){
				_.extend(this.state, {uriAvailable: false, candidateUri: null})
				this.publishState()
				return
			}

			var regex = new RegExp(/https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)/gi);
			var uri = uriOrSuffix.match(regex)

			if(!uri || uri.length == 0){
				var uriBase = this.state.types[this.state.selectedIdx].newInstanceBaseUri;
				var uri = uriBase + encodeURIComponent(uriOrSuffix.trim().replace(/ /g, '_'))
			}

			_.extend(this.state, {candidateUri: uri, uriAvailable: undefined})

			var self = this;
			uriCheck(uri, function(checkRes){
				if(uri === self.state.candidateUri){
					_.extend(self.state, checkRes);
					self.publishState();
				}
			})
		}
	});
}
