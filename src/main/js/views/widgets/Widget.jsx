module.exports = React.createClass({

	render: function(){

		var cssClasses = "panel panel-" + this.props.widgetType;

		return <div className={cssClasses}>
			<div className="panel-heading" title={this.props.headerTitle}>
				<h3 className="panel-title">{this.props.widgetTitle}</h3><
			/div>
			<div className="panel-body">{this.props.children}</div>
		</div>;
	}

});

