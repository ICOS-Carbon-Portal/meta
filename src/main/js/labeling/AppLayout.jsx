var actions = Reflux.createActions([
	"chooseStation"
]);

var Backend = require('./backend.js');
var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, actions.chooseStation);
var StationsList = require('./views/StationsListFactory.jsx')(StationsListStore, actions.chooseStation);

module.exports = React.createClass({
	render: () =>
		<div className="container-fluid">
			<div className="row">
				<div className="col-md-2"><StationsList /></div>
			</div>
		</div>
});

