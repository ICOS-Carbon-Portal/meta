module.exports = React.createClass({

	getInitialState: function(){
		return {readyToDelete: false};
	},

	activateDeletion: function(event){
		this.setState({readyToDelete: true});
	},

	abortDeletion: function(){
		this.setState({readyToDelete: false});
	},

	performDeletion: function(event){
		event.stopPropagation();
		this.props.individual.deletionHandler();
	},

	render: function(){

		var ind = this.props.individual;
		var readyDelete = this.state.readyToDelete;
		var itemClasses = "list-group-item list-group-item-" + (ind.isChosen ? "info" : "default");
		var buttonClasses = "input-group-btn" + (readyDelete ? " open" : "");
		var ariaExpanded = readyDelete ? "true" : "false";
		var glyph = "glyphicon glyphicon-" + (readyDelete ? "arrow-up" : "remove");
		var preDeleteClick = readyDelete ? this.abortDeletion : this.activateDeletion;

		return <li className={itemClasses} title={ind.displayName} onClick={ind.clickHandler} style={{padding: '0px'}} role="menuitem">

			<div className="input-group">

				<input type="text" value={ind.displayName} className="cp-lnk form-control"
					style={{textOverflow: 'ellipsis', backgroundColor: 'transparent'}} readOnly />

				<div className={buttonClasses}>

					<button
						type="button" onClick={preDeleteClick} className="btn btn-default dropdown-toggle"
						data-toggle="dropdown" aria-haspopup="true" aria-expanded={ariaExpanded}
						style={{backgroundColor: 'transparent'}}>
						<span className={glyph}></span>
					</button>

					<ul className="dropdown-menu dropdown-menu-right" onMouseLeave={this.abortDeletion}>
						<li><a>Delete this individual?</a></li>
						<li role="separator" className="divider"></li>
						<li><a><button className="btn btn-danger" onClick={this.performDeletion}>
								Delete <span className="glyphicon glyphicon-remove"></span>
							</button></a>
						</li>
					</ul>

				</div>
			</div>

		</li>;
	}
});
