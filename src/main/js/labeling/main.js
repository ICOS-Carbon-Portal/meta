'use strict';

import StationsListFactory from './views/StationsListFactory.jsx';

var actions = Reflux.createActions([
	'chooseStation',
	'saveStation',
	'fileUpload',
	'fileDelete',
	'statusUpdate',
	'statusCommentUpdate',
	'savePi',
	'stationFilters'
]);

var ajax = require('../common/ajax.js');
var sparql = require('../common/sparql.js')(ajax, '/sparql');
var Backend = require('./backend.js')(ajax, sparql);

var WhoAmIStore = require('./stores/WhoAmIStoreFactory.js')(Backend, actions.savePi);

var ChosenStationStore = require('./stores/ChosenStationStoreFactory.js')(Backend, actions.chooseStation, actions.saveStation, actions.statusUpdate, actions.statusCommentUpdate);

var StationsListStore = require('./stores/StationsListStoreFactory.js')(Backend, ChosenStationStore);

var StationFilterStore = require('./stores/StationFilterStoreFactory.js')(actions.stationFilters);
var StationFilteringStore = require('./stores/StationFilteringStoreFactory.js')(StationsListStore, StationFilterStore);

var StationAuthStore = require('./stores/StationAuthStoreFactory.js')(WhoAmIStore, StationFilteringStore);

var FileAwareStationStore = require('./stores/FileAwareStationStoreFactory.js')(Backend, ChosenStationStore, actions.fileUpload, actions.fileDelete);

var StationMixins = require('./views/StationMixinsFactory.jsx')(
	FileAwareStationStore,
	actions.fileUpload,
	actions.fileDelete,
	actions.saveStation,
	actions.statusUpdate,
	actions.statusCommentUpdate
);

var themeToStation = {
	Atmosphere: require('./views/AtmosphereStationFactory.jsx')(StationMixins),
	Ecosystem: require('./views/EcosystemStationFactory.jsx')(StationMixins),
	Ocean: require('./views/OceanStationFactory.jsx')(StationMixins)
};

var StationFilter = require('./views/StationFilterFactory.jsx')(StationFilterStore, actions.stationFilters);

var StationsList = StationsListFactory(StationAuthStore, themeToStation, actions.chooseStation);
var NavBar = require('./views/NavBarFactory.jsx')(WhoAmIStore);
var PiInfo = require('./views/PiInfoFactory.jsx')(WhoAmIStore, actions.savePi);

var AppLayout = require('./views/AppLayoutFactory.jsx')(NavBar, StationsList, PiInfo, StationFilter);

React.render(
	React.createElement(AppLayout, null),
	document.getElementById('main')
);

