var MetadataType = require('./MetadataType.jsx');

module.exports = React.createClass({
	getInitialState: function(){
		return {
			types: ['Station', 'Person']
		};
	},
	render: function(){
		return <ul>
			{this.state.types.map(function(theType){
				return <MetadataType displayName={theType} />;
			})}
		</ul>;
	}
});
