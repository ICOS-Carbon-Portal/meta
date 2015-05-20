var capped = require('./utils.js').ensureLength(30);

module.exports = function(individualsStore){

	return React.createClass({
		mixins: [Reflux.connect(individualsStore)],
		render: function(){
			return <div className="btn-group-vertical" role="group">
					{this.state.individuals.map(function(individual){
						var fullName = individual.displayName;
						return <button title={fullName} type="button" className="btn btn-default" key={individual.uri}>
								{capped(fullName)}
						</button>;
					})}
			</div>;
		}
	});
}
