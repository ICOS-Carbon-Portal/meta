var WidgetButton = require('./WidgetButton.jsx');

module.exports = React.createClass({

	render: function(){

		var cssClasses = "panel panel-" + this.props.widgetType;
		var buttons = this.props.buttons || [];

		return <div className={cssClasses}>

			<div className="panel-heading" title={this.props.headerTitle}>

				<h3 style={{display: "inline"}} className="panel-title">{this.props.widgetTitle}</h3>

				<div style={{display: "inline", float: "right"}}>{
					_.map(buttons, function(buttProps, i){
						var props = _.extend({}, buttProps, {key: "propButton_" + i});
						return <WidgetButton {...props}/>;
					})
				}</div>

			</div>

			<div className="panel-body">{this.props.children}</div>

		</div>;
	}

});

