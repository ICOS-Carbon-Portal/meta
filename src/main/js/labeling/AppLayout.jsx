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
var StationsList = require('./views/StationsListFactory.jsx')(StationAuthStore, actions.chooseStation, actions.saveStation, actions.fileUploadAction);
var FileUploadStore = require('./stores/FileUploadStoreFactory.js')(Backend, actions.fileUploadAction);

module.exports = React.createClass({
	render: () =>
		<div>
			<div>
				<div><StationsList /></div>
			</div>
		</div>
});

