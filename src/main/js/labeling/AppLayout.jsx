var actions = Reflux.createActions([
	'chooseStation',
	'saveStation',
	'fileUpload',
	'fileDelete',
	'labelingStart'
]);

var ajax = require('../common/ajax.js');
var sparql = require('../common/sparql.js')(ajax, '/sparql');
var Backend = require('./backend.js')(ajax, sparql);

var WhoAmIStore = require('./stores/WhoAmIStoreFactory.js')(Backend);
var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, actions.chooseStation, actions.saveStation);
var StationAuthStore = require('./stores/StationAuthStoreFactory.js')(WhoAmIStore, StationsListStore);

var ChosenStationStore = require('./stores/ChosenStationStoreFactory.js')(Backend, actions.chooseStation, actions.saveStation);
var FileAwareStationStore = require('./stores/FileAwareStationStoreFactory.js')(Backend, ChosenStationStore, actions.fileUpload, actions.fileDelete);

var FileManager = require('./views/FileManagerFactory.jsx')(FileAwareStationStore, actions.fileUpload, actions.fileDelete);

var NavBar = require('./views/NavBarFactory.jsx')(WhoAmIStore);


var StationMixins = require('./views/StationMixinsFactory.jsx')(FileAwareStationStore, FileManager, actions.saveStation, actions.labelingStart);

var themeToStation = {
	Atmosphere: require('./views/AtmosphereStationFactory.jsx')(StationMixins),
	Ecosystem: require('./views/EcosystemStationFactory.jsx')(StationMixins),
	Ocean: require('./views/OceanStationFactory.jsx')(StationMixins)
};

var StationsList = require('./views/StationsListFactory.jsx')(StationAuthStore, themeToStation, actions.chooseStation);

module.exports = React.createClass({
	render: () =>
		<div>
			<NavBar />
			<StationsList />
		</div>
});

