
module.exports = function(editStore){

	return React.createClass({
	
		mixins: [Reflux.connect(editStore)],
		
		render: function(){
		
			return <div>{

					JSON.stringify(this.state.individual || "")
						
			}</div>;
		}
	});
}
