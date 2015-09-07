var capped = require('../utils.js').ensureLength(45);
var ChoiceButton = require('./ChoiceButton.jsx');

module.exports = function(individualsStore, chooseAction){

	return React.createClass({
	
		mixins: [Reflux.connect(individualsStore)],
		
		render: function(){
			var self = this;

			var individuals = _.chain(this.state.individuals)
				.map(function(individual){
					return _.extend({}, individual, {
						shortName: capped(individual.displayName),
						clickHandler: _.partial(chooseAction, individual.uri),
						isChosen: (individual.uri == self.state.chosen)
					});
				})
				.sortBy("displayName")
				.value();

			return <div className="btn-group-vertical" role="group">{
					individuals.map(function(ind){
						return <ChoiceButton key={ind.uri} chosen={ind.isChosen} tooltip={ind.displayName}
							clickHandler={ind.clickHandler} style={{"text-align": "left"}}>{ind.shortName}</ChoiceButton>;
					})
			}</div>;
		}
	});
}
