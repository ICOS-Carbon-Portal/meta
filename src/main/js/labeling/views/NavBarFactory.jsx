module.exports = function(WhoAmIStore) { 

	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore,"user")],

		render: function() {
			var loggedIn = (this.state.user.mail !== 'dummy@dummy.none');

			return <nav className="navbar navbar-default navbar-static-top">
				<div className="container-fluid">
					<div className="navbar-right" style={{marginTop: 7, marginRight: 3}}>
						{loggedIn ? <p>Logged in as {this.state.user.mail}</p> : <p>Not logged in</p>}
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

