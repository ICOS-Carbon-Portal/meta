module.exports = function(WhoAmIStore) { 

	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore, "user")],

		render: function() {
			var loggedIn = (this.state.user.mail !== 'dummy@dummy.none');

			var title = this.props.piMode
				? "PI Information"
				: "ICOS Station Labeling";

			return <nav className="navbar navbar-default navbar-static-top">
				<div className="container-fluid">
					<div className="navbar-right" style={{marginTop: 7, marginRight: 3}}>
						{loggedIn ? <p>Logged in as {this.state.user.mail}</p> : <p>Not logged in</p>}
						{loggedIn ? null : <p>Log in <a href="./login">here</a></p>}
						{this.state.user.isPi
							? this.props.piMode
								? <p>Back to <a href="#">station labeling</a></p>
								: <p>Edit your info <a href="#piinfo">here</a></p>
							: null}
					</div>
					<div className="navbar-header">
						<img alt="Carbon Portal" src="https://static.icos-cp.eu/images/Icos_Logo_RGB_White_Carbon_Portal_80px.png" />
					</div>
					<h1>{title}</h1>
				</div>
			</nav>;
		}

	});
}

