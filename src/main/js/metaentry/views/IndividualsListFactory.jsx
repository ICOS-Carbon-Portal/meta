var IndividualListItem = require('./IndividualListItem.jsx');
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

module.exports = function(individualsStore, chooseAction, removeAction, IndividualAdder){

	return React.createClass({

		mixins: [Reflux.connect(individualsStore)],

		render: function(){
			var self = this;

			var buttons = [{
				icon: 'plus',
				isDisabled: this.state.addingInstance,
				clickHandler: function(){
					self.setState({addingInstance: true});
				}
			}];

			var individuals = _.chain(this.state.individuals)
				.map(function(individual){
					return _.extend({}, individual, {
						clickHandler: _.partial(chooseAction, individual.uri),
						deletionHandler: _.partial(removeAction, individual.uri),
						isChosen: (individual.uri === self.state.chosen)
					});
				})
				.sortBy("displayName")
				.value();

			return <Widget widgetTitle="Entries" buttons={buttons}>

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
