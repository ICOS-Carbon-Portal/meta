import ContentPanel from './ContentPanel.jsx';
import {statusFullText, statusClass} from './StationsListFactory.jsx';

export default function(saveStationAction) {

	const LifecycleButton = React.createClass({

		render: function(){
			const props = this.props;
			if(!props.active) return null;

			return <button type="button" className={'btn ' + props.buttonClass}
					onClick={() => saveStationAction(props.getUpdated())} title={props.tooltip} style={{marginRight: 5}}>{props.buttonName}</button>;
		}
	});

	const LifecycleControls = React.createClass({

		render: function(){
			let status = this.props.status;
			if(!status.canControlLifecycle) return null;

			return <div style={{marginTop: 15}}>

				<LifecycleButton buttonClass="btn-primary" active={status.canBeAcknowledged} getUpdated={() => status.getAcknowledged()}
					tooltip="Acknowledge the step 1 application" buttonName="Acknowledge step 1" />

				<LifecycleButton buttonClass="cp-btn-gray" active={status.canBeReturned} getUpdated={() => status.getReturned()}
					tooltip="Return the step 1 application for correction and resubmission" buttonName="Return step 1" />

				<LifecycleButton buttonClass="btn-success" active={status.canBeApproved} getUpdated={() => status.getApproved()}
					tooltip="Approve the step 1 of labeling" buttonName="Approve step 1" />

				<LifecycleButton buttonClass="btn-danger" active={status.canBeRejected} getUpdated={() => status.getRejected()}
					tooltip="Reject the application at step 1" buttonName="Reject step 1" />

				<LifecycleButton buttonClass="btn-warning" active={status.step2CanStart} getUpdated={() => status.getStep2Started()}
					tooltip="Start step 2 of labeling" buttonName="Start step 2" />

				<LifecycleButton buttonClass="btn-success" active={status.step2CanBeDecided} getUpdated={() => status.getStep2Approved()}
					tooltip="Approve step 2 of labeling" buttonName="Approve step 2" />

				<LifecycleButton buttonClass="btn-danger" active={status.step2CanBeDecided} getUpdated={() => status.getStep2Rejected()}
					tooltip="Reject step 2 of labeling" buttonName="Reject step 2" />

				<LifecycleButton buttonClass="btn-danger" active={status.step3CanBeDecided} getUpdated={() => status.getStep3Approved()}
					tooltip="Grant final approval to the labeling application" buttonName="Grant final approval" />

			</div>;
		}

	});

	return React.createClass({

		render: function() {
			const appStatus = this.props.status.value;

			return <ContentPanel panelTitle="Application status">

				<h3 style={{marginTop: 0, marginBottom: 5}}>
					<span className={statusClass(appStatus)}>
						{statusFullText(appStatus)}
					</span>
				</h3>

				<LifecycleControls status={this.props.status} />

			</ContentPanel>;
		}
	});

};

