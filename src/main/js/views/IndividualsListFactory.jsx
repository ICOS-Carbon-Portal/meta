var capped = require('../utils.js').ensureLength(30);

module.exports = function(individualsStore, chooseAction){

	return React.createClass({
	
		mixins: [Reflux.connect(individualsStore)],
		
		render: function(){
		
			return <div className="btn-group-vertical" role="group">{

					this.state.individuals.map(function(individual){
					
						var fullName = individual.displayName;
						var shortName = capped(fullName);
						var uri = individual.uri;
						var clickHandler = _.partial(chooseAction, individual);
						
						return <button
							type="button"
							className="btn btn-default"
							key={uri}
							title={fullName}
							onClick={clickHandler}>{shortName}</button>;
						
					})
			}</div>;
		}
	});
}
