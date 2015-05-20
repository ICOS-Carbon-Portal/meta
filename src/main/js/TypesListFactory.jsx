
module.exports = function(typesStore, chooseTypeAction){

	var MetadataType = require('./MetadataTypeFactory.jsx')(chooseTypeAction);

	return React.createClass({
		mixins: [Reflux.connect(typesStore)],
		render: function(){
			return <div className="btn-group-vertical" role="group">
					{this.state.types.map(function(theType){
						return <MetadataType {...theType}  key={theType.uri}/>;
					})}
			</div>;
		}
	});
}
