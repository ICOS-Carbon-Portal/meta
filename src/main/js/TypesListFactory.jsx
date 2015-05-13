
module.exports = function(typesStore, chooseTypeAction){

	var MetadataType = require('./MetadataTypeFactory.jsx')(chooseTypeAction);

	return React.createClass({
		mixins: [Reflux.connect(typesStore)],
		render: function(){
			return <table className="table">
				<tbody>
					{this.state.types.map(function(theType){
						return <tr key={theType.uri}>
							<td>
								<MetadataType {...theType} />
							</td>
						</tr>;
					})}
				</tbody>
			</table>;
		}
	});
}