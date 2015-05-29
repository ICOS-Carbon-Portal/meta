var Individual = require('../models/Individual.js');

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

			return <div>
				<Widget widgetTitle="Entry" isStatic="true">{individ.getLabel()}</Widget>
				<Widget widgetTitle="Entry type" isStatic="true">{individ.getClassInfo().displayName}</Widget>{

					_.map(individ.getPropertyValues(), function(propValues, i){
						var key = "owlProp_" + i;

						var selector = propValues.getValuesType() == "dataProperty"
							? function(val){return val.getValue();}
							: _.property("displayName");

						var presentation = _.map(propValues.getValues(), selector).join(", ");

						var title = propValues.getPropertyInfo().displayName;

						return <Widget key={key} widgetTitle={title}>{presentation}</Widget>;

					})

				}
			</div>;
		}
	});
}

