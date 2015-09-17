var actions = Reflux.createActions([
	"chooseMetaType",
	"chooseIndividual",
	"requestUpdate",
	"checkUriSuffix",
	"createIndividual",
	"removeIndividual"
]);

var Backend = require('./backend.js');

var TypesStore = require('./stores/TypesStoreFactory.js')(Backend, actions.chooseMetaType, actions.checkUriSuffix);
var IndividualsStore = require('./stores/IndividualsStoreFactory.js')(Backend, actions.chooseMetaType, actions.chooseIndividual, actions.createIndividual, actions.removeIndividual);
var EditStore = require('./stores/EditStoreFactory.js')(Backend, actions.chooseIndividual, actions.requestUpdate);

var TypesList = require('./views/TypesListFactory.jsx')(TypesStore, actions.chooseMetaType);
var IndividualAdder = require('./views/IndividualAdderFactory.jsx')(TypesStore, actions.checkUriSuffix, actions.createIndividual);
var IndividualsList = require('./views/IndividualsListFactory.jsx')(IndividualsStore, actions.chooseIndividual, actions.removeIndividual, IndividualAdder);
var EditView = require('./views/EditViewFactory.jsx')(EditStore, actions.requestUpdate);
var ScreenHeightColumn = require('./views/ScreenHeightColumn.jsx');

module.exports = React.createClass({
	render: function(){

		return <div className="container-fluid">
			<div className="row" style={{marginTop: "2px"}}>
				<div className="col-md-2"><TypesList /></div>
				<div className="col-md-3"><IndividualsList /></div>
				<div className="col-md-7"><EditView /></div>
			</div>
		</div>;
	}
});

