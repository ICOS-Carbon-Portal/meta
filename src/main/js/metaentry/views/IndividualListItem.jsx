module.exports = React.createClass({

	performDeletion: function(event){
		event.stopPropagation();
		this.props.individual.deletionHandler();
	},

	render: function(){

		var ind = this.props.individual;
		var itemClasses = "list-group-item" + (ind.isChosen ? " list-group-item-info" : "");

		return <li className={itemClasses} title={ind.displayName} onClick={ind.clickHandler} style={{padding: '0px'}} role="menuitem">

			<div className="input-group">

				<input type="text" value={ind.displayName} className="cp-lnk form-control border-0"
					style={{textOverflow: 'ellipsis', backgroundColor: 'transparent'}} readOnly />

				<div className="dropdown">

					<button
						type="button" className="btn dropdown-toggle"
						data-bs-toggle="dropdown" aria-haspopup="true"
						style={{backgroundColor: 'transparent'}}>
						<span className="fas fa-times"></span>
					</button>

					<ul className="dropdown-menu">
						<li>
							<button className="dropdown-item text-danger" onClick={this.performDeletion}>
								<span className="fas fa-trash me-2"></span>
								Confirm deletion
							</button>
						</li>
					</ul>

				</div>
			</div>

		</li>;
	}
});
