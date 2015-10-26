import ContentPanel from './ContentPanel.jsx';

export default function(saveStationAction) {

	const LifecycleControls = React.createClass({

		render: function(){
			let status = this.props.status;
			if(!status.canControlLifecycle) return null;

			return <div style={{marginTop: 15}}>
				<button type="button" className="btn btn-primary" disabled={!status.canBeAcknowledged}
					onClick={this.getHandler(s => s.getAcknowledged())} title="Acknowledge the application">Acknowledge</button>

				<button type="button" className="btn btn-warning" disabled={!status.canBeReturned} style={{marginLeft: 5}}
					onClick={this.getHandler(s => s.getReturned())} title="Return for correction and resubmission">Return</button>

				<button type="button" className="btn btn-success" disabled={!status.canBeApproved} style={{marginLeft: 5}}
					onClick={this.getHandler(s => s.getApproved())} title="Approve the application and label the station">APPROVE</button>

				<button type="button" className="btn btn-danger" disabled={!status.canBeRejected} style={{marginLeft: 5}}
					onClick={this.getHandler(s => s.getRejected())} title="Reject the application">REJECT</button>
			</div>;
		},

		getHandler: function(statusToUpdated){
			const self = this;
			return function(){
				let updatedStation = statusToUpdated(self.props.status);
				saveStationAction(updatedStation);
			};
		}

	});


	return React.createClass({

		render: function() {
			let {kind: labelClass, text: statusLabel} = this.props.status.label;

			return <ContentPanel panelTitle="Application status">

				<h3 style={{marginTop: 0, marginBottom: 5}}>
					<span className={'label label-' + labelClass}>
						{statusLabel}
					</span>
				</h3>

				<LifecycleControls status={this.props.status} />

			</ContentPanel>;
		}
	});

};

