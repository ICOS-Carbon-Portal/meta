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
		this.restoringPath = initialPath.typeName || initialPath.individualName ? initialPath : null;
		return {selectedType: null, selectedIndividual: null};
	},

	isRestoredPath: function(storeState){
		if(!this.restoringPath) return false;

		var selectedTypeName = storeState.selectedType ? urlManager.pathName(storeState.selectedType) : null;
		if(selectedTypeName !== this.restoringPath.typeName) return false;

		if(!this.restoringPath.individualName) return storeState.selectedIndividual == null;

		if(storeState.selectedIndividual && urlManager.pathName(storeState.selectedIndividual) === this.restoringPath.individualName) return true;

		return storeState.loadingIndividuals === false && !urlManager.findByPathName(storeState.individuals, this.restoringPath.individualName);
	},

	componentDidMount: function(){
		this.handlePopState = function(){
			var path = urlManager.readPath();
			this.restoringPath = path;
			actions.selectIndividualByPath(path.individualName);
			actions.selectMetaTypeByPath(path.typeName);
		}.bind(this);
		window.addEventListener('popstate', this.handlePopState);

		this.listenTo(actions.userSelectMetaType, function(typeUri){
			this.restoringPath = null;
			actions.selectMetaType(typeUri);
			urlManager.updatePath(typeUri, null);
		}.bind(this));

		this.listenTo(TypesStore, function(s){
			this.setState({selectedType: s.selected});
		}.bind(this));
		this.listenTo(IndividualsStore, function(s){
			var replaceTypeOnlyPath = this.state.selectedIndividual == null && s.selectedIndividual != null;
			this.setState({selectedIndividual: s.selectedIndividual});
			if(this.restoringPath) {
				if(this.isRestoredPath(s)) {
					var hadIndividualTarget = !!this.restoringPath.individualName;
					this.restoringPath = null;
					if(hadIndividualTarget && !s.selectedIndividual)
						urlManager.updatePath(s.selectedType, null, true);
				}
				return;
			}
			urlManager.updatePath(s.selectedType, s.selectedIndividual, replaceTypeOnlyPath);
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
