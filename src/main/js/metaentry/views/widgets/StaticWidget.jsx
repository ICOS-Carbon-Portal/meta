var Widget = require('./Widget.jsx');

module.exports = React.createClass({

	render: function(){

		return <Widget
			widgetTitle={this.props.widgetTitle}
			widgetType="success">
			{this.props.children}
		</Widget>;
	}

});

