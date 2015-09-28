var actions = Reflux.createActions([
	'chooseStation',
	'saveStation',
	'fileUploadAction'
]);

var ajax = require('../common/ajax.js');
var sparql = require('../common/sparql.js')(ajax, '/sparql');
var Backend = require('./backend.js')(ajax, sparql);

var WhoAmIStore = require('./stores/WhoAmIStoreFactory.js')(Backend);
var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, actions.chooseStation, actions.saveStation);
var StationAuthStore = require('./stores/StationAuthStoreFactory.js')(WhoAmIStore, StationsListStore);
var FileUploadStore = require('./stores/FileUploadStoreFactory.js')(Backend, actions.fileUploadAction);

var NavBar = require('./views/NavBarFactory.jsx')(WhoAmIStore);
var StationsList = require('./views/StationsListFactory.jsx')(StationAuthStore, actions.chooseStation, actions.saveStation, actions.fileUploadAction);

module.exports = React.createClass({
	render: () =>
		<div>
			<NavBar />
			<StationsList />
		</div>
});

