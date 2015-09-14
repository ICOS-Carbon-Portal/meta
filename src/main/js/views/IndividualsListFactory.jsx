var capped = require('../utils.js').ensureLength(45);
var ChoiceButton = require('./ChoiceButton.jsx');
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

module.exports = function(individualsStore, chooseAction, IndividualAdder){

	return React.createClass({

		mixins: [Reflux.connect(individualsStore)],

		render: function(){
			var self = this;

			var buttons = [{
				glyphicon: 'plus',
				isDisabled: this.state.addingInstance,
				clickHandler: function(){
					self.setState({addingInstance: true});
				}
			}];

			var individuals = _.chain(this.state.individuals)
				.map(function(individual){
					return _.extend({}, individual, {
						shortName: capped(individual.displayName),
						clickHandler: _.partial(chooseAction, individual.uri),
						isChosen: (individual.uri == self.state.chosen)
					});
				})
				.sortBy("displayName")
				.value();

			return <Widget widgetType="primary" widgetTitle="Entries" buttons={buttons}>

				{this.state.addingInstance ? <IndividualAdder cancelHandler={this.hideAdder}/> : null}

				<ScreenHeightColumn>
					<div className="btn-group-vertical" role="group">{
						individuals.map(function(ind){
							return <ChoiceButton key={ind.uri} chosen={ind.isChosen} tooltip={ind.displayName}
								clickHandler={ind.clickHandler} style={{"text-align": "left"}}>{ind.shortName}</ChoiceButton>;
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

