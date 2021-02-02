function getRoute(){
	return {
		route: window.location.hash.substr(1),
		toasterState: undefined
	};
}

module.exports = function (NavBar, StationsList, PiInfo, StationFilter, Toaster, ToasterStore){
	return React.createClass({

		mixins: [Reflux.connect(ToasterStore)],

		getInitialState: getRoute,

		componentDidMount: function() {
			var self = this;
			window.addEventListener('hashchange', () => {
				self.setState(getRoute());
			})
		},

		render: function () {
			return this.state.route === "piinfo"
				? <div>
					<NavBar piMode={true} />
					<PiInfo />
				</div>
				: <div>
					<NavBar piMode={false} />
					<Toaster toasters={ToasterStore.state} removeToasterHandler={ToasterStore.removeToasterHandler} />
					<StationFilter />
					<StationsList />
				</div>;
		}
	});
};

