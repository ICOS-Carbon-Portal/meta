var capped = require('../utils.js').ensureLength(30);
var ChoiceButton = require('./ChoiceButton.jsx');

module.exports = function(individualsStore, chooseAction){

	return React.createClass({
	
		mixins: [Reflux.connect(individualsStore)],
		
		render: function(){
			var self = this;

			return <div className="btn-group-vertical" role="group">{

					this.state.individuals.map(function(individual){
					
						var fullName = individual.displayName;
						var shortName = capped(fullName);
						var uri = individual.uri;
						var clickHandler = _.partial(chooseAction, individual.uri);
						var isChosen = (uri == self.state.chosen);

						return <ChoiceButton key={uri} chosen={isChosen} tooltip={fullName} clickHandler={clickHandler}>{shortName}</ChoiceButton>;
					})
			}</div>;
		}
	});
}
