var ContentPanel = require('./ContentPanel.jsx');

module.exports = function(WhoAmIStore){
	return React.createClass({

		mixins: [Reflux.connect(WhoAmIStore, "user")],

		render: function() {

			if(!this.state.user.isPi) return null;

			return <ContentPanel panelTitle="User information">
				<form role="form" onSubmit={this.submissionHandler}>

					<div>{`PI info editing for ${this.state.user.firstName} is to be here`}</div>

					<button type="submit" className="btn btn-primary" disabled={!this.state.canSave}>Save</button>
				</form>
			</ContentPanel>;
		},

		submissionHandler: function(event){
			event.preventDefault();
		}
	});
};

