import ContentPanel from './ContentPanel.jsx';
import { statusFullText, statusClass, statusLabel} from './StationsListFactory.jsx';

import { status } from '../models/ApplicationStatus.js';
import Inputs from './FormInputs.jsx';

function getTransitionText(from, to){
	switch(to){
		case status.acknowledged: return ["Acknowledge Step 1", "Acknowledge the Step 1 application"];
		case status.notSubmitted: return ["Return Step 1", "Return the Step 1 application for correction and resubmission"];
		case status.approved: return from === status.step2ontrack
			? ["Cancel Step 2", "Reset the status back to \"Step 1 approved\""]
			: ["Approve Step 1", "Approve Step 1 of labeling for this station"];
		case status.rejected: return ["Reject Step 1", "Reject the application at Step 1"];
		case status.step2ontrack:
			return from === status.approved
				? ["Start Step 2", "Start Step 2 of station labeling"]
				: ["Reset Step 2", "Change application status back to \"Started Step 2\""];
		case status.step2approved:
			return from === status.step3approved
				? ["Revoke final approval", "Reset the status back to \"Step 2 approved\""]
				: ["Approve Step 2", "Approve Step 2 of station's labeling procedure"];
		case status.step2delayed: return ["Delay Step 2", "Delay the application at Step 2"];
		case status.step2stalled: return ["Stall Step 2", "Stall the application at Step 2"];
		case status.step3approved: return ["Grant final approval", "Make this an official ICOS station"];
		default: return ["Unsupported status", "Unsupported status: " + to];
	}
}

function transitionButtonClass(to){
	switch(to){
		case status.acknowledged: return "btn-primary";
		case status.notSubmitted: return "cp-btn-gray";
		case status.approved: return "btn-success";
		case status.rejected: return "btn-danger";
		case status.step2ontrack : return "btn-warning";
		case status.step2approved : return "btn-success";
		case status.step2delayed: return "btn-warning";
		case status.step2stalled : return "btn-danger";
		case status.step3approved : return "btn-success";
	}
}

function transitionLabelClass(to) {
	switch (to) {
		case status.acknowledged: return "label-primary";
		case status.notSubmitted: return "label-default";
		case status.approved: return "label-success";
		case status.rejected: return "label-danger";
		case status.step2ontrack: return "label-warning";
		case status.step2approved: return "label-success";
		case status.step2delayed: return "label-warning";
		case status.step2stalled: return "label-danger";
		case status.step3approved: return "label-success";
	}
}

const defaultAppStatusCommentPholder = 'Comment about application status';
function getAppStatusCommentPholder(hasApplicationStatus) {
	switch (hasApplicationStatus) {
		case 'STEP2DELAYED':
			return 'Provide a reason for why this station is delayed';
		
		case 'STEP2STALLED':
			return 'Provide a reason for why this station is stalled';
		
		default:
			return defaultAppStatusCommentPholder;
	}
}

export default function (ToasterStore, updateStatusAction) {

	const LifecycleRadioButton = React.createClass({

		render: function () {
			const props = this.props;

			return (
				<label className={props.buttonClass} style={{marginRight: 20, fontSize: '110%', cursor: 'pointer'}} title={props.tooltip}>
					<input
						type="radio"
						name="appStatusComment"
						checked={props.checked}
						className="radio-inline"
						style={{margin: '0px 5px 0px 0px'}}
						onClick={() => props.updateStatus()}
					/>
					{props.buttonName}
				</label>
			);
		}
	});

	const LifecycleControls = React.createClass({

		render: function(){
			const { status, updateStatus, selectedStatus } = this.props;
			if (!status.canControlLifecycle) return null;

			return <div style={{marginTop: 25}}>{
				_.map(status.transitions, to => {
					let [buttonLabel, helpText] = getTransitionText(status.value, to);
					const isSelected = selectedStatus === to;
					const buttonClass = 'label ' + transitionLabelClass(to);

					return (
						<LifecycleRadioButton
							key={to}
							checked={isSelected}
							buttonClass={buttonClass}
							updateStatus={() => updateStatus(to)}
							tooltip={helpText}
							buttonName={buttonLabel}
						/>
					);
				})}
				<LifecycleRadioButton
					checked={selectedStatus === status.value}
					buttonClass={statusClass(status.value)}
					updateStatus={() => updateStatus(status.value)}
					tooltip={'Keep current application status'}
					buttonName={statusLabel(status.value)}
				/>
			</div>;
		}

	});

	const AppStatusCtrl = React.createClass({
		getInitialState: function () {
			return {
				hasApplicationStatus: this.props.status.station['hasApplicationStatus'],
				hasAppStatusComment: this.props.status.station['hasAppStatusComment'],
				appStatusCommentPholder: defaultAppStatusCommentPholder,
				isSubmitDisabled: true
			};
		},

		updateState: function (statePart) {
			this.setState(Object.assign({}, this.state, statePart));
		},

		updateStatus: function (newStatus) {
			const { hasAppStatusComment } = this.state;
			const isSubmitDisabled = this.getIsSubmitDisabled(newStatus, hasAppStatusComment);
			const appStatusCommentPholder = getAppStatusCommentPholder(newStatus);
			this.updateState({ hasApplicationStatus: newStatus, appStatusCommentPholder, isSubmitDisabled });
		},

		getIsSubmitDisabled: function (hasApplicationStatus, hasAppStatusComment) {
			return (hasApplicationStatus === 'STEP2DELAYED' || hasApplicationStatus === 'STEP2STALLED') && !hasAppStatusComment;
		},

		updateStatusComment: function (errors, newAppStatusComment) {
			const { hasApplicationStatus, hasAppStatusComment } = this.state;

			if (newAppStatusComment !== hasAppStatusComment) {
				const isSubmitDisabled = this.getIsSubmitDisabled(hasApplicationStatus, newAppStatusComment);
				this.updateState({ hasAppStatusComment: newAppStatusComment, isSubmitDisabled });
			}
		},

		submitAppStatus: function () {
			const { hasApplicationStatus, hasAppStatusComment } = this.state;
			const currStation = this.props.status.station;
			const newStation = this.props.status
				.withStatus(hasApplicationStatus)
				.stationWithStatusComment(hasAppStatusComment);
			
			const isUpdatedStatus = currStation.hasApplicationStatus !== newStation.hasApplicationStatus;
			const isUpdatedStatusComment = currStation.hasAppStatusComment !== newStation.hasAppStatusComment;

			if (isUpdatedStatus || isUpdatedStatusComment) {
				updateStatusAction(newStation);
			
			} else {
				ToasterStore.showToasterHandler('No changes to save', 'warning');
			}
		},

		render: function () {
			const status = this.props.status;
			const { hasApplicationStatus, hasAppStatusComment, appStatusCommentPholder, isSubmitDisabled } = this.state;
			const disabled = !status.station.isUsersTcStation;

			return (
				<div style={{ marginTop: 15 }}>
					<LifecycleControls status={this.props.status} updateStatus={this.updateStatus} selectedStatus={hasApplicationStatus} />

					<Inputs.TextAreaWithBtn
						value={hasAppStatusComment}
						optional={true}
						disabled={disabled}
						btnDisabled={isSubmitDisabled}
						updater={this.updateStatusComment}
						placeholder={appStatusCommentPholder}
						btnTxt="Save application status"
						btnAction={this.submitAppStatus}
					/>
				</div>
			);
		}
	});

	return React.createClass({

		render: function () {
			const appStatus = this.props.status.value;

			return <ContentPanel panelTitle="Application status">

				<h3 style={{marginTop: 0, marginBottom: 5}}>
					<span className={statusClass(appStatus)}>
						{statusFullText(appStatus)}
					</span>
				</h3>

				<AppStatusCtrl status={this.props.status} />

			</ContentPanel>;
		}
	});

};
