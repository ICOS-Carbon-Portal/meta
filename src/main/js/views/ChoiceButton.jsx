module.exports = React.createClass({
	render: function(){

		var classes = "btn" + (this.props.chosen ? " btn-info" : " btn-default");

		return <button type="button" title={this.props.tooltip} className={classes} onClick={this.props.clickHandler}>
			{this.props.children}
		</button>;
	}
});
	
