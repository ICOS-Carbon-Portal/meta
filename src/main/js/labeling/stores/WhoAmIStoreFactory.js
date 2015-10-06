function defaultUser() {
	return {
		mail: "dummy@dummy.none",
		givenName: "",
		surname: ""
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

