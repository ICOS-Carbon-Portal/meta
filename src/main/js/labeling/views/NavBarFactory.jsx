module.exports = function(WhoAmIStore) {

	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore, "user")],

		render: function() {
			var loggedIn = (this.state.user.mail !== 'dummy@dummy.none');

			return <div className="page-header">
				<div className="pull-right">
					{loggedIn ? <p>Logged in as {this.state.user.mail}</p> : <p>Not logged in</p>}
					{loggedIn ? null : <p>Log in <a href="https://cpauth.icos-cp.eu/login/?targetUrl=https://meta.icos-cp.eu/labeling/">here</a></p>}
					{this.state.user.isPi
						? this.props.piMode
							? <p>Back to <a href="#">station labeling</a></p>
							: <p>Edit your info <a href="#piinfo">here</a></p>
						: null}
				</div>
				{this.props.piMode
					? <h1>PI Information <small>for ICOS metadata</small></h1>
					: <div>
							<h1>Station labelling status</h1>
							<div>The labelling status for ICOS stations. You can read more about our data collection process <a href="https://www.icos-cp.eu/data-services/data-collection/labelling-process">here</a>.</div>
							<div><a href="https://static.icos-cp.eu/share/stations/docs/labelling/labelingStateDiagram.svg">State diagram for application process</a></div>
						</div>
				}
			</div>;
		}

	});
}

