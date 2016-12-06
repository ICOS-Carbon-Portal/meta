import ContentPanel from './ContentPanel.jsx';
import {statusFullText, statusClass} from './StationsListFactory.jsx';
import {status} from '../models/ApplicationStatus.js';

function getTransitionText(from, to){
	switch(to){
		case status.acknowledged: return ["Acknowledge step 1", "Acknowledge the step 1 application"];
		case status.notSubmitted: return ["Return step 1",      "Return the step 1 application for correction and resubmission"];
		case status.approved: return ["Approve step 1",     "Approve step 1 of labeling for this station"];
		case status.rejected: return ["Reject step 1",      "Reject the application at step 1"];
	}
}

function transitionButtonClass(to){
	switch(to){
		case status.acknowledged: return "btn-primary";
		case status.notSubmitted: return "cp-btn-gray";
		case status.approved: return "btn-success";
		case status.rejected: return "btn-danger";
		case status.step2started : return "btn-warning";
		case status.step2approved : return "btn-success";
		case status.step2rejected : return "btn-danger";
		case status.step3approved : return "btn-success";
	}
}

export default function(saveStationAction) {

	const LifecycleButton = React.createClass({

		render: function(){
			const props = this.props;

			return <button type="button" className={'btn ' + props.buttonClass}
					onClick={() => saveStationAction(props.getUpdated())} title={props.tooltip} style={{marginRight: 5}}>{props.buttonName}</button>;
		}
	});

	const LifecycleControls = React.createClass({

		render: function(){
			let status = this.props.status;
			if(!status.canControlLifecycle) return null;

			return <div style={{marginTop: 15}}>{
				_.map(status.transitions, to => {
					let [buttonLabel, helpText] = getTransitionText(status.value, to);

					return <LifecycleButton key={to} buttonClass={transitionButtonClass(to)}
						getUpdated={() => status.stationWithStatus(to)}
						tooltip={helpText} buttonName={buttonLabel}
					/>;
				})
			}</div>;
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

