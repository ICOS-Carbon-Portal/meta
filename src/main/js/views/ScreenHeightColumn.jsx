module.exports = React.createClass({
	componentDidMount: function(){
		var self = this;
		self.ensureScreenHeight();
		this.resizeListener = _.throttle(
			function(){
				self.ensureScreenHeight();
			},
			200,
			{leading: false}
		),
		window.addEventListener("resize", this.resizeListener);
	},
	componentWillUnmount: function(){
		window.removeEventListener("resize", this.resizeListener);
	},
	ensureScreenHeight: function(){
		var elem = React.findDOMNode(this);
		var top = elem.getBoundingClientRect().top;
		var desiredHeight = window.innerHeight - top - 20;
		elem.style.height = desiredHeight + "px";
	},
	render: function(){
		return <div className={this.props.className} style={{overflowY: "auto", overflowX: "hidden"}}>
			{this.props.children}
		</div>;
	}
});
