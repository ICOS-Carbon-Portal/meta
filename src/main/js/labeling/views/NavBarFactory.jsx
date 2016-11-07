module.exports = function(WhoAmIStore) { 

	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore, "user")],

		render: function() {
			var loggedIn = (this.state.user.mail !== 'dummy@dummy.none');

			return <div className="page-header">
				<div className="pull-right">
					{loggedIn ? <p>Logged in as {this.state.user.mail}</p> : <p>Not logged in</p>}
					{loggedIn ? null : <p>Log in <a href="https://cpauth.icos-cp.eu/login/?targetUrl=http://meta.icos-cp.eu/labeling/">here</a></p>}
					{this.state.user.isPi
						? this.props.piMode
							? <p>Back to <a href="#">station labeling</a></p>
							: <p>Edit your info <a href="#piinfo">here</a></p>
						: null}
				</div>
				{this.props.piMode
					? <h1>PI Information <small>for ICOS metadata</small></h1>
					: <h1>ICOS Station Labeling <small>Step 1</small></h1>}
			</div>;
		}

	});
}

