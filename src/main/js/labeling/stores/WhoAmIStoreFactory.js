function defaultUser() {
	return {
		mail: "guest@icos-cp.eu",
		givenName: "Guest",
		surname: "at Carbon Portal"
	};
}

module.exports = function(Backend){

	return Reflux.createStore({

		getInitialState: defaultUser,

		init: function(){
			Backend.whoAmI()
				.catch(errRep => defaultUser())
				.then(
					_.bind(this.trigger, this),
					err => console.log(err)
				);
		}

	});
}

