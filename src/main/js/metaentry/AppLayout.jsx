var actions = Reflux.createActions([
	"selectMetaType",
	"selectMetaTypeByPath",
	"userSelectMetaType",
	"selectIndividual",
	"selectIndividualByPath",
	"requestUpdate",
	"checkUriOrSuffix",
	"createIndividual",
	"removeIndividual"
]);

var Backend = require('./backend.js');
var urlManager = require('./urlManager.js');

var initialPath = urlManager.readPath();

var TypesStore = require('./stores/TypesStoreFactory.js')(Backend, actions.selectMetaType, actions.selectMetaTypeByPath, actions.checkUriOrSuffix, initialPath.typeName);
var IndividualsStore = require('./stores/IndividualsStoreFactory.js')(Backend, actions.selectMetaType, actions.selectIndividual, actions.selectIndividualByPath, actions.createIndividual, actions.removeIndividual, initialPath.individualName);
var EditStore = require('./stores/EditStoreFactory.js')(Backend, actions.selectIndividual, actions.requestUpdate);

var TypesList = require('./views/TypesListFactory.jsx')(TypesStore, actions.userSelectMetaType);
var IndividualAdder = require('./views/IndividualAdderFactory.jsx')(TypesStore, actions.checkUriOrSuffix, actions.createIndividual);
var IndividualsList = require('./views/IndividualsListFactory.jsx')(IndividualsStore, actions.selectIndividual, actions.removeIndividual, IndividualAdder);
var EditView = require('./views/EditViewFactory.jsx')(EditStore, actions.requestUpdate);

module.exports = React.createClass({
	mixins: [Reflux.ListenerMixin],

	getInitialState: function(){
		return {selectedType: null, selectedIndividual: null};
	},

	componentDidMount: function(){
		this.handlePopState = function(){
			var path = urlManager.readPath();
			this.isRestoringFromPopstate = true;
			actions.selectIndividualByPath(path.individualName);
			actions.selectMetaTypeByPath(path.typeName);
			this.isRestoringFromPopstate = false;
		}.bind(this);
		window.addEventListener('popstate', this.handlePopState);

		this.listenTo(actions.userSelectMetaType, function(typeUri){
			actions.selectMetaType(typeUri);
			urlManager.updatePath(typeUri, null);
		}.bind(this));

		this.listenTo(TypesStore, function(s){
			this.setState({selectedType: s.selected});
		}.bind(this));
		this.listenTo(IndividualsStore, function(s){
			var replaceTypeOnlyPath = this.state.selectedIndividual == null && s.selectedIndividual != null;
			this.setState({selectedIndividual: s.selectedIndividual});
			if(!this.isRestoringFromPopstate) urlManager.updatePath(s.selectedType, s.selectedIndividual, replaceTypeOnlyPath);
		}.bind(this));
	},

	componentWillUnmount: function(){
		window.removeEventListener('popstate', this.handlePopState);
	},

	render: function(){
		return <div className="row" style={{marginTop: "2px"}}>
			<div className="col-md-2"><TypesList /></div>
			<div className="col-md-3"><IndividualsList /></div>
			<div className="col-md-7"><EditView /></div>
		</div>;
	}
});
