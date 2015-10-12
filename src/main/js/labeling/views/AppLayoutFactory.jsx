function getRoute(){
	return {route: window.location.hash.substr(1)};
}

module.exports = function(NavBar, StationsList, PiInfo){
	return React.createClass({

		getInitialState: getRoute,

		componentDidMount: function() {
			var self = this;
			window.addEventListener('hashchange', () => {
				self.setState(getRoute());
			})
		},

		render: function() {
			return this.state.route === "piinfo"
				? <div>
					<NavBar piMode={true} />
					<PiInfo />
				</div>
				: <div>
					<NavBar piMode={false} />
					<StationsList />
				</div>;
		}
	});
};

