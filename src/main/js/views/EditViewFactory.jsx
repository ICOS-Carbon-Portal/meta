var Individual = require('../models/Individual.js');
var Widgets = require('./Widgets.jsx');

var Widget = React.createClass({

	render: function(){

		var panelType = this.props.isStatic
			? "panel panel-success"
			: "panel panel-info";

		return <div className={panelType}>
			<div className="panel-heading"><h3 className="panel-title">{this.props.widgetTitle}</h3></div>
			<div className="panel-body">{this.props.children}</div>
		</div>;
	}

});

module.exports = function(editStore){

	return React.createClass({

		mixins: [Reflux.connect(editStore)],

		render: function(){

			if(!this.state.individual) return <div></div>;

			var individ = new Individual(this.state.individual);
			var individKey = individ.getKey() + "_";

			return <div>
				<Widgets.Static widgetTitle="Entry">{individ.getLabel()}</Widgets.Static>
				<Widgets.Static widgetTitle="Entry type">{individ.getClassInfo().displayName}</Widgets.Static>{

					_.map(individ.getPropertyValues(), function(propValues, i){

						var key = individKey + propValues.getKey();

						return <Widgets.Property key={key} propertyValues={propValues} />;

					})

				}
			</div>;
		}
	});
}

