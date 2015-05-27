
module.exports = function(editStore){

	return React.createClass({
	
		mixins: [Reflux.connect(editStore)],
		
		render: function(){

			if(!this.state.individual) return <div>Choose an entry to edit here</div>;
			
			var individ = this.state.individual;
			var label = individ.resource.displayName;
			var typeName = individ.owlClass.resource.displayName;
			
			
			return <div>
				<h3>Entry: <span className="label label-default">{label}</span></h3>
				<h3>Entry type: <span className="label label-default">{typeName}</span></h3>
			</div>;
		}
	});
}
