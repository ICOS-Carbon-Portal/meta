var actions = Reflux.createActions([
	"selectMetaType",
	"selectIndividual",
	"requestUpdate",
	"checkUriOrSuffix",
	"createIndividual",
	"removeIndividual"
]);

var Backend = require('./backend.js');

var TypesStore = require('./stores/TypesStoreFactory.js')(Backend, actions.selectMetaType, actions.checkUriOrSuffix);
var IndividualsStore = require('./stores/IndividualsStoreFactory.js')(Backend, actions.selectMetaType, actions.selectIndividual, actions.createIndividual, actions.removeIndividual);
var EditStore = require('./stores/EditStoreFactory.js')(Backend, actions.selectIndividual, actions.requestUpdate);

var TypesList = require('./views/TypesListFactory.jsx')(TypesStore, actions.selectMetaType);
var IndividualAdder = require('./views/IndividualAdderFactory.jsx')(TypesStore, actions.checkUriOrSuffix, actions.createIndividual);
var IndividualsList = require('./views/IndividualsListFactory.jsx')(IndividualsStore, actions.selectIndividual, actions.removeIndividual, IndividualAdder);
var EditView = require('./views/EditViewFactory.jsx')(EditStore, actions.requestUpdate);

module.exports = React.createClass({
	render: function(){

		return <div className="row" style={{marginTop: "2px"}}>
			<div className="col-md-2"><TypesList /></div>
			<div className="col-md-3"><IndividualsList /></div>
			<div className="col-md-7"><EditView /></div>
		</div>;
	}
});
