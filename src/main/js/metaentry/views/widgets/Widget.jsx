var WidgetButton = require('./WidgetButton.jsx');

module.exports = React.createClass({

	render: function(){

		var background = this.props.widgetType ? " bg-" + this.props.widgetType : "";
		var cssClasses = "card-header" + background;
		var buttons = this.props.buttons || [];

		return <div className="card mb-3">

			<div className={cssClasses} title={this.props.headerTitle}>

				<h3 style={{ display: "inline" }} className="fs-6">{this.props.widgetTitle}</h3>

				<div style={{display: "inline", float: "right"}}>{
					_.map(buttons, function(buttProps, i){
						var props = _.extend({}, buttProps, {key: "propButton_" + i});
						return <WidgetButton {...props}/>;
					})
				}</div>

			</div>

			<div className="card-body">{this.props.children}</div>

		</div>;
	}

});

