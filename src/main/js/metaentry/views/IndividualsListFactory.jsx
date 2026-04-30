var IndividualListItem = require('./IndividualListItem.jsx');
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

function submitSparqlQuery(query){
	var form = document.createElement('form');
	form.method = 'POST';
	form.action = '/sparqlclient/';
	form.target = '_blank';
	form.style.display = 'none';

	var queryInput = document.createElement('input');
	queryInput.type = 'hidden';
	queryInput.name = 'query';
	queryInput.value = query;
	form.appendChild(queryInput);

	document.body.appendChild(form);
	form.submit();
	document.body.removeChild(form);
}

module.exports = function(individualsStore, selectAction, removeAction, IndividualAdder){

	return React.createClass({

		mixins: [Reflux.connect(individualsStore)],

		render: function(){
			var self = this;

			if(!this.state.selectedType) return null;

			var buttons = [{
				icon: 'share-square',
				label: 'View SPARQL query',
				noLeftMargin: true,
				isDisabled: !this.state.individualsSparql,
				clickHandler: function(){
					if(!self.state.individualsSparql) return;
					submitSparqlQuery(self.state.individualsSparql);
				}
			}, {
				icon: 'plus',
				label: 'Add entry',
				btnVariant: 'btn-primary',
				isDisabled: this.state.addingInstance,
				clickHandler: function(){
					self.setState({addingInstance: true});
				}
			}];

			var individuals = _.chain(this.state.individuals)
				.map(function(individual){
					var isSelected = (individual.uri === self.state.selectedIndividual);
					return _.extend({}, individual, {
						clickHandler: _.partial(selectAction, individual.uri),
						deletionHandler: _.partial(removeAction, individual.uri),
						isSelected: isSelected
					});
				})
				.sortBy("displayName")
				.value();

			return <Widget widgetTitle="Entries" buttons={buttons} buttonsInBody={true}>

				{this.state.addingInstance ? <IndividualAdder cancelHandler={this.hideAdder}/> : null}

				<ScreenHeightColumn>
					<div className="list-group" role="menu">{
						individuals.map(function(ind){
							return <IndividualListItem key={ind.uri} individual={ind} />;
						})
					}</div>
				</ScreenHeightColumn>

			</Widget>;
		},

		hideAdder: function(){
			this.setState({addingInstance: false});
		}

	});
}
