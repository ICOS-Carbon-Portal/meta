var actions = Reflux.createActions([
	"chooseMetaType",
	"chooseIndividual"
]);

var Backend = require('./backend.js');

var TypesStore = require('./stores/TypesStoreFactory.js')(Backend, actions.chooseMetaType);
var IndividualsStore = require('./stores/IndividualsStoreFactory.js')(Backend, actions.chooseMetaType, actions.chooseIndividual);
var EditStore = require('./stores/EditStoreFactory.js')(Backend, actions.chooseIndividual);

var TypesList = require('./views/TypesListFactory.jsx')(TypesStore, actions.chooseMetaType);
var IndividualsList = require('./views/IndividualsListFactory.jsx')(IndividualsStore, actions.chooseIndividual);
var EditView = require('./views/EditViewFactory.jsx')(EditStore);
var ScreenHeightColumn = require('./views/ScreenHeightColumn.jsx');

var PanelHeader = React.createClass({
	render: function(){
		return <div className={this.props.className}>
			<div className="panel panel-primary">
				<div className="panel-heading">
    				<h2 className="panel-title">{this.props.title}</h2>
				</div>
			</div>
		</div>;
	}
});

module.exports = React.createClass({
	render: function(){

		return <div className="container-fluid">
			<div className="row" style={{marginTop: "5px"}}>
				<PanelHeader className="col-md-2" title="Types" />
				<PanelHeader className="col-md-3" title="Entries" />
				<PanelHeader className="col-md-7" title="Entry editor" />
			</div>
			<div className="row">
				<ScreenHeightColumn className="col-md-2"><TypesList /></ScreenHeightColumn>
				<ScreenHeightColumn className="col-md-3"><IndividualsList /></ScreenHeightColumn>
				<ScreenHeightColumn className="col-md-7"><EditView /></ScreenHeightColumn>
			</div>
		</div>;
	}
});

