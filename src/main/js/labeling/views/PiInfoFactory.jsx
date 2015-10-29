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

					<Inputs.String {...this.getProps('mail')} disabled="true" header="Email" />
					<Inputs.String {...this.getProps('firstName')} header="First name" />
					<Inputs.String {...this.getProps('lastName')} header="Last name" />
					<Inputs.String {...this.getProps('affiliation')} optional="true" header="Affiliation" />
					<Inputs.Phone {...this.getProps('phone')} optional="true" header="Phone" />

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

		getProps: function(propName){
			var user = this.state.user;
			return {
				updater: this.getUpdater(propName),
				value: user[propName]
			};
		},

		canSave: function(){
			return _.isEmpty(_.flatten(_.values(this.state.errors))) && !_.isEqual(this.state.user, this.state.userOriginal);
		}

	});
};

