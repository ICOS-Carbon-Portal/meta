module.exports = function(WhoAmIStore) { 

	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore,"user")],

		render: function() {
			var loggedIn = (this.state.user.givenName !== 'Guest');
			var user = loggedIn ? this.state.user.mail : 'Guest';


			return <nav className="navbar navbar-default navbar-static-top">
				<div className="container-fluid">
					<div className="navbar-right" style={{"margin-top": '7px', "margin-right": '3px'}}>
						<p>Logged in as: {user}</p>
						{loggedIn ? null : <p>Log in <a href="./login">here</a></p>}
					</div>
					<div className="navbar-header">
						<img alt="Carbon Portal" src="https://static.icos-cp.eu/images/Icos_Logo_RGB_White_Carbon_Portal_80px.png" />
					</div>
					<h1>ICOS Station Labeling</h1>
				</div>
			</nav>;
		}

	});
}

