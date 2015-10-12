module.exports = function(Backend){

	return Reflux.createStore({

		getInitialState: function(){
			return this.user;
		},

		publish: function(userInfo){
			this.user = userInfo;
			this.trigger(this.user);
		},

		init: function(){
			this.user = {
				mail: "dummy@dummy.none",
				isPi: false,
				firstName: "",
				lastName: ""
			};
			Backend.whoAmI()
				.catch(errRep => defaultUser())
				.then(
					_.bind(this.publish, this),
					err => console.log(err)
				);
		}

	});
}

