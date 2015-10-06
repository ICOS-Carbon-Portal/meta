var capped = require('../utils.js').ensureLength(30);
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

module.exports = function(typesStore, chooseTypeAction){

	return React.createClass({

		mixins: [Reflux.connect(typesStore)],

		render: function(){
			var self = this;

			return <Widget widgetType="primary" widgetTitle="Types">
				<ScreenHeightColumn>
					<div className="list-group">{

						this.state.types.map(function(theType){

							var clickHandler = _.partial(chooseTypeAction, theType.uri);
							var isChosen = (theType.uri == self.state.chosen);
							var fullName = theType.displayName;

							return <li
								className={"cp-lnk list-group-item list-group-item-" + (isChosen ? "info" : "default")}
								key={theType.uri} title={fullName} onClick={clickHandler}>
								{capped(fullName)}
							</li>;
						})
				
					}</div>
				</ScreenHeightColumn>
			</Widget>;
		}

	});
}

