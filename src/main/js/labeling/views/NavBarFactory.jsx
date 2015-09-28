module.exports = function(WhoAmIStore) { 

	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore,"user")],

		render: function() {
			var user = this.state.user;

			return <nav className="navbar navbar-default navbar-fixed-top">
				<div className="navbar-right">Logged in as:<br />{user.givenName} {user.surname}<br />({user.mail})</div>
			</nav>;
		}

	});
}

