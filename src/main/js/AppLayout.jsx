var actions = Reflux.createActions([
	"chooseMetaType"
]);

var Backend = require('./dataFetcher.js');
var TypesStore = require('./TypesStoreFactory.js')(actions.chooseMetaType, Backend);
var IndividualsStore = require('./IndividualsStoreFactory.js')(actions.chooseMetaType, Backend);
var TypesList = require('./TypesListFactory.jsx')(TypesStore, actions.chooseMetaType);
var IndividualsList = require('./IndividualsListFactory.jsx')(IndividualsStore);

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
				{entryEditCol()}
			</div>
		</div>;
	}
});

