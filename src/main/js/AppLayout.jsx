var actions = Reflux.createActions([
	"chooseMetaType"
]);

var TypesStore = require('./TypesStoreFactory.js')(actions.chooseMetaType);
var TypesList = require('./TypesListFactory.jsx')(TypesStore, actions.chooseMetaType);

module.exports = React.createClass({
	render: function(){

		function colDiv(colClass, content){
			return <div className={colClass}>{content}</div>;
		}

		var entryTypeCol = _.partial(colDiv, "col-md-1");
		var entryListCol = _.partial(colDiv, "col-md-1");
		var entryEditCol = _.partial(colDiv, "col-md-10");

		return <div className="container-fluid">
			<div className="row">
				{entryTypeCol(<h4>Types of entry</h4>)}
				{entryListCol(<h4>Existing entries</h4>)}
				{entryEditCol(<h4>Entry editor</h4>)}
			</div>
			<div className="row">
				{entryTypeCol(<TypesList />)}
				{entryListCol()}
				{entryEditCol()}
			</div>
		</div>;
	}
});

