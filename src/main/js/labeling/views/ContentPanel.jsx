module.exports = React.createClass({
	render: function(){
		return <div className="card mb-3">
			<div className="card-header"><h3 className="fs-6 d-inline">{this.props.panelTitle}</h3></div>
			<div className="card-body">{this.props.children}</div>
		</div>
	}
});
