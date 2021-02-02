import {stationOwlClassToTheme} from '../configs.js';

function defaultUser(){
	return {
		mail: "dummy@dummy.none",
		isPi: false,
		tcs: [],
		firstName: "",
		lastName: ""
	};
}

function tcsToThemes(userInfo){
	return _.extend({}, userInfo, {
		tcs: _.map(userInfo.tcs || [], stationOwlClassToTheme)
	});
}

export default function (Backend, ToasterStore, savePiAction){

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
					_.compose(_.bind(this.publish, this), tcsToThemes),
					err => ToasterStore.showToasterHandler(err.message)
				);
		},

		savePiHandler: function(pi){
			Backend.saveUserInfo(pi).then(
				_.bind(this.publish, this, pi),
				err => ToasterStore.showToasterHandler(err.message)
			);
		}

	});
}

