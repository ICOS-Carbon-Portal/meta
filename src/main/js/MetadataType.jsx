module.exports = React.createClass({
	getInitialState: function(){
		return {
			selected: false
		};
	},
	handleClick: function(){
		this.setState({selected: !this.state.selected});
	},
	render: function(){
		var classes = "label" + (this.state.selected ? " label-primary" : " label-info");
		return <li onClick={this.handleClick}>
					<span className={classes}>{this.props.displayName}</span>
				</li>;
	}
});
