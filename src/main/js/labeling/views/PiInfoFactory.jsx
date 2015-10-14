var ContentPanel = require('./ContentPanel.jsx');
var Inputs = require('./FormInputs.jsx');

module.exports = function(WhoAmIStore, savePiAction){
	return React.createClass({

		mixins: [Reflux.connectFilter(WhoAmIStore, function(storeState){
			return {
				user: storeState,
				userOriginal: storeState,
				errors: {}
			};
		})],

		render: function() {

			if(!this.state.user.isPi) return <div>You are not a PI!</div>;

			return <ContentPanel panelTitle="Your information">
				<form role="form" onSubmit={this.submissionHandler}>

					<Inputs.Group title="Email"><Inputs.String {...this.getProps('mail', true)} /></Inputs.Group>
					<Inputs.Group title="First name"><Inputs.StringRequired {...this.getProps('firstName')} /></Inputs.Group>
					<Inputs.Group title="Last name"><Inputs.StringRequired {...this.getProps('lastName')} /></Inputs.Group>
					<Inputs.Group title="Affiliation"><Inputs.String {...this.getProps('affiliation')} /></Inputs.Group>
					<Inputs.Group title="Phone"><Inputs.Phone {...this.getProps('phone')} /></Inputs.Group>

					<button type="submit" className="btn btn-primary" disabled={!this.canSave()}>Save</button>
				</form>
			</ContentPanel>;
		},

		submissionHandler: function(event){
			event.preventDefault();
			savePiAction(this.state.user);
		},

		getUpdater: function(propName){
			var self = this;

			return (errors, newValue) => {
				var newState = _.clone(self.state);
				newState.errors = _.clone(self.state.errors);

				newState.errors[propName] = errors;
				newState.valid = _.isEmpty(_.flatten(_.values(newState.errors)));

				if(!_.isUndefined(newValue)){
					newState.user = _.clone(self.state.user);
					newState.user[propName] = newValue;
				}

				if(!_.isEqual(newState.user, self.state.user)) self.setState(newState);
			};
		},

		getProps: function(propName, disabled){
			var user = this.state.user;
			return {
				updater: this.getUpdater(propName),
				value: user[propName],
				disabled: !!disabled
			};
		},

		canSave: function(){
			return _.isEmpty(_.flatten(_.values(this.state.errors))) && !_.isEqual(this.state.user, this.state.userOriginal);
		}

	});
};

