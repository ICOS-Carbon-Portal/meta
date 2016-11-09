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
		var listElem = React.findDOMNode(this);

		var listRect = listElem.getBoundingClientRect();
		var panelRect = listElem.parentElement.parentElement.getBoundingClientRect();

		var totalMargin = panelRect.height - listRect.height;

		var desiredHeight = window.innerHeight - totalMargin - 10;

		listElem.style.height = desiredHeight + "px";
	},
	render: function(){
		return <div className={this.props.className} style={{overflowY: "auto", overflowX: "hidden"}}>
			{this.props.children}
		</div>;
	}
});
