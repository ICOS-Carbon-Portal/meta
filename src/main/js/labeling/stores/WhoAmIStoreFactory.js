function defaultUser(){
	return {
		mail: "dummy@dummy.none",
		isPi: false,
		firstName: "",
		lastName: ""
	};
}

module.exports = function(Backend, savePiAction){

	return Reflux.createStore({

		getInitialState: function(){
			return this.user;
		},

		publish: function(userInfo){
			this.user = userInfo;
			this.trigger(this.user);
		},

		init: function(){
			this.user = defaultUser();

			this.listenTo(savePiAction, this.savePiHandler);

			Backend.whoAmI()
				.catch(errRep => defaultUser())
				.then(
					_.bind(this.publish, this),
					err => console.log(err)
				);
		},

		savePiHandler: function(pi){
			Backend.saveUserInfo(pi).then(
				_.bind(this.publish, this, pi),
				err => console.log(err)
			);
		}

	});
}

