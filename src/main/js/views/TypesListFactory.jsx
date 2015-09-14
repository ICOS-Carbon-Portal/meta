var capped = require('../utils.js').ensureLength(30);
var ChoiceButton = require('./ChoiceButton.jsx');
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

module.exports = function(typesStore, chooseTypeAction){

	return React.createClass({

		mixins: [Reflux.connect(typesStore)],

		render: function(){
			var self = this;

			return <Widget widgetType="primary" widgetTitle="Types">
				<ScreenHeightColumn>
					<div className="btn-group-vertical" role="group">{

						this.state.types.map(function(theType){

							var clickHandler = _.partial(chooseTypeAction, theType.uri);
							var isChosen = (theType.uri == self.state.chosen);
							var fullName = theType.displayName;

							return <ChoiceButton key={theType.uri} chosen={isChosen} tooltip={fullName} clickHandler={clickHandler}>{capped(fullName)}</ChoiceButton>;
						})
				
					}</div>
				</ScreenHeightColumn>
			</Widget>;
		}

	});
}

