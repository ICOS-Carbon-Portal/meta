var WidgetButton = require('./WidgetButton.jsx');

module.exports = React.createClass({

		render: function(){

			var background = this.props.widgetType ? " bg-" + this.props.widgetType : "";
			var cssClasses = "card-header" + background;
			var buttons = this.props.buttons || [];
			var showButtonsInHeader = !this.props.buttonsInBody;

			var renderedButtons = _.map(buttons, function(buttProps, i){
				var props = _.extend({}, buttProps, {key: "propButton_" + i});
				return <WidgetButton {...props}/>;
			});

			return <div className="card mb-3">

				<div className={cssClasses} title={this.props.headerTitle}>
					<div className="d-flex justify-content-between align-items-center">
						<h3 className="fs-6" style={{marginBottom: 0}}>{this.props.widgetTitle}</h3>
						{showButtonsInHeader ? <div>{renderedButtons}</div> : null}
					</div>

				</div>

				<div className="card-body">
					{this.props.buttonsInBody ? <div className="d-flex flex-column flex-xl-row flex-xl-wrap justify-content-between gap-2 mb-2">{renderedButtons}</div> : null}
					{this.props.children}
				</div>

			</div>;
		}

});
