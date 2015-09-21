module.exports = function(Backend){
	
	return Reflux.createStore({

		getInitialState: function(){
			return {
			    email: "guest@icos-cp.eu",
                firstName: "Guest",
	            lastName: "Guest"
			};
		},
	                          
		init: function(){
			var self = this;
			Backend.whoAmI().then(function(whoami){
				self.trigger(whoami);
			});
		}
		
	});
	
	
}
