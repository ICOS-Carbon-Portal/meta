var actions = Reflux.createActions([
	"chooseStation"
]);

var Backend = require('./backend.js');
var WhoAmIStore = require('./stores/WhoAmIStoreFactory.js')(Backend);
var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, actions.chooseStation);
var StationAuthStore = require('./stores/StationAuthStoreFactory.js')(WhoAmIStore, StationsListStore);
var StationsList = require('./views/StationsListFactory.jsx')(StationAuthStore, actions.chooseStation);

module.exports = React.createClass({
	render: () =>
		<div>
			<div>
				<div><StationsList /></div>
			</div>
		</div>
});

