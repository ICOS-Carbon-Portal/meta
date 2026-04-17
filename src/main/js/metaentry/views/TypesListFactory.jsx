var capped = require('../utils.js').ensureLength(30);
var Widget = require('./widgets/Widget.jsx');
var ScreenHeightColumn = require('./ScreenHeightColumn.jsx');

module.exports = function(typesStore, selectTypeAction){

	return React.createClass({

		mixins: [Reflux.connect(typesStore)],

		render: function(){
			var self = this;

			return <Widget widgetTitle="Types">
				<ScreenHeightColumn>
					<div className="list-group" role="menu">{

					this.state.types.map(function(theType){

						var clickHandler = _.partial(selectTypeAction, theType.uri);
						var isSelected = (theType.uri == self.state.selected);
						var fullName = theType.displayName;

						return <li
							className={"cp-lnk list-group-item" + (isSelected ? " list-group-item-info" : "")}
							key={theType.uri} title={fullName} onClick={clickHandler} role="menuitem">
							{capped(fullName)}
							</li>;
						})

					}</div>
				</ScreenHeightColumn>
			</Widget>;
		}

	});
}
