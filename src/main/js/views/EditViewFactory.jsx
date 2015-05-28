
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
			
			var individ = this.state.individual;
			var label = individ.resource.displayName;
			var typeName = individ.owlClass.resource.displayName;
			
			var propsToVals = _.object(
				_.map(individ.values, function(indValue){
					var presentation = indValue.type == "object"
						? indValue.value.displayName
						: indValue.value;
					return [indValue.property.uri, presentation];
				})
			);
			
			return <div>
				<Widget widgetTitle="Entry" isStatic="true">{label}</Widget>
				<Widget widgetTitle="Entry type" isStatic="true">{typeName}</Widget>{
				
					_.map(individ.owlClass.properties, function(prop, i){
						var key = "owlProp_" + i;
						
						var presentation = propsToVals[prop.resource.uri] || "";

						return <Widget key={key} widgetTitle={prop.resource.displayName}>{presentation}</Widget>;
						
					})
					
				}
			</div>;
		}
	});
}
