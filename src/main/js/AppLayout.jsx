var actions = Reflux.createActions([
	"chooseMetaType",
	"chooseIndividual"
]);

var Backend = require('./backend.js');

var TypesStore = require('./stores/TypesStoreFactory.js')(Backend, actions.chooseMetaType);
var IndividualsStore = require('./stores/IndividualsStoreFactory.js')(Backend, actions.chooseMetaType);
var EditStore = require('./stores/EditStoreFactory.js')(Backend, actions.chooseIndividual);

var TypesList = require('./views/TypesListFactory.jsx')(TypesStore, actions.chooseMetaType);
var IndividualsList = require('./views/IndividualsListFactory.jsx')(IndividualsStore, actions.chooseIndividual);
var EditView = require('./views/EditViewFactory.jsx')(EditStore);

module.exports = React.createClass({
	render: function(){

		function colDiv(colClass, content){
			return <div className={colClass}>{content}</div>;
		}

		var entryTypeCol = _.partial(colDiv, "col-md-1");
		var entryListCol = _.partial(colDiv, "col-md-2");
		var entryEditCol = _.partial(colDiv, "col-md-9");

		return <div className="container-fluid">
			<div className="row">
				{entryTypeCol(<h4>Types of entry</h4>)}
				{entryListCol(<h4>Existing entries</h4>)}
				{entryEditCol(<h4>Entry editor</h4>)}
			</div>
			<div className="row">
				{entryTypeCol(<TypesList />)}
				{entryListCol(<IndividualsList />)}
				{entryEditCol(<EditView />)}
			</div>
		</div>;
	}
});

