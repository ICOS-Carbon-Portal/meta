module.exports = function(Backend){

	return Reflux.createStore({

		getInitialState: function(){
			return {
				mail: "",
				givenName: "",
				surname: ""
			};
		},

		init: function(){
			Backend.whoAmI().then(_.bind(this.trigger, this));
		}

	});
}

