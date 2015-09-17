var actions = Reflux.createActions([
	"myaction"
]);

var Backend = require('./backend.js');

module.exports = React.createClass({
	render: function(){

		return <div className="container-fluid">
			<div className="row">
				<div><p>Hello, labelers!</p></div>
			</div>
		</div>;
	}
});

