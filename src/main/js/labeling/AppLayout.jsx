var actions = Reflux.createActions([
	'chooseStation',
	'saveStation',
	'fileUpload',
	'fileDelete'
]);

var ajax = require('../common/ajax.js');
var sparql = require('../common/sparql.js')(ajax, '/sparql');
var Backend = require('./backend.js')(ajax, sparql);

var WhoAmIStore = require('./stores/WhoAmIStoreFactory.js')(Backend);
var FileStore = require('./stores/FileStoreFactory.js')(Backend, actions.fileUpload, actions.fileDelete);
var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, FileStore, actions.chooseStation, actions.saveStation);
var StationAuthStore = require('./stores/StationAuthStoreFactory.js')(WhoAmIStore, StationsListStore);
var StationFileAwareStore = require('./stores/StationFileAwareStoreFactory.js')(StationAuthStore);

var NavBar = require('./views/NavBarFactory.jsx')(WhoAmIStore);

var FileManager = require('./views/FileManagerFactory.jsx')(actions.fileUpload, actions.fileDelete);
var StationsList = require('./views/StationsListFactory.jsx')(StationFileAwareStore, FileManager, actions.chooseStation, actions.saveStation);

module.exports = React.createClass({
	render: () =>
		<div>
			<NavBar />
			<StationsList />
		</div>
});

