var actions = Reflux.createActions([
	'chooseObjectAction'
]);

var ajax = require('../common/ajax.js');
var sparql = require('../common/sparql.js')(ajax, '/sparql');
var Backend = require('./backend.js')(ajax, sparql);
var WhoAmIStore = require('./stores/WhoAmIStoreFactory.js')(Backend);
var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, actions.chooseObjectAction);
var StationAuthStore = require('./stores/StationAuthStoreFactory.js')(WhoAmIStore, StationsListStore);
var StationsList = require('./views/StationsListFactory.jsx')(StationAuthStore, actions.chooseObjectAction);

module.exports = React.createClass({
	render: () =>
		<div>
			<div>
				<div><StationsList /></div>
			</div>
		</div>
});

