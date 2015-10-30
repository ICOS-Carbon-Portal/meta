export const status = {
	notSubmitted: 'NOT SUBMITTED',
	submitted: 'SUBMITTED',
	acknowledged: 'ACKNOWLEDGED',
	approved: 'APPROVED',
	rejected: 'REJECTED'
};

export default class ApplicationStatus{

	constructor(station){
		this.station = station;
	}

	get mayBeSubmitted(){
		let appStatus = this.station.hasApplicationStatus;
		return this.station.isUsersStation && (
			!appStatus || appStatus === status.notSubmitted
		);
	}

	get canBeAcknowledged(){
		return this.station.hasApplicationStatus === status.submitted;
	}

	get canBeReturned(){
		let appStatus = this.station.hasApplicationStatus;
		return !!appStatus && (appStatus !== status.notSubmitted);
	}

	get canBeApproved(){
		return this.canBeReturned && this.station.hasApplicationStatus !== status.approved;
	}

	get canBeRejected(){
		return this.canBeReturned && this.station.hasApplicationStatus !== status.rejected;
	}

	get canControlLifecycle(){
		return this.station.isUsersTcStation;
	}

	getSubmitted(){
		this.assert(this.mayBeSubmitted, 'This station is already submitted or you are not allowed to submit');
		return this.withStatus(status.submitted);
	}

	getAcknowledged(){
		this.assert(this.canBeAcknowledged, 'Cannot acknowledge this application!');
		return this.withStatus(status.acknowledged);
	}

	getReturned(){
		this.assert(this.canBeReturned, 'Cannot return this application for resubmission!');
		return this.withStatus(status.notSubmitted);
	}

	getApproved(){
		this.assert(this.canBeApproved, 'Cannot approve this application!');
		return this.withStatus(status.approved);
	}

	getRejected(){
		this.assert(this.canBeRejected, 'Cannot reject this application!');
		return this.withStatus(status.rejected);
	}

	withStatus(newStatus){
		return _.extend({}, this.station, {hasApplicationStatus: newStatus});
	}

	assert(condition, errorMessage){
		if(!condition) throw new Error(errorMessage);
	}

	get label(){
		let appStatus = this.station.hasApplicationStatus;
		if(appStatus === status.submitted)
			return {kind: 'info', text: 'Application submitted, waiting for acknowlegment and review'};
		else if(appStatus === status.acknowledged)
			return {kind: 'primary', text: 'Application sumbission acknowledged, waiting for review'};
		else if(appStatus === status.approved)
			return {kind: 'success', text: 'Application has been approved!'};
		else if(appStatus === status.rejected)
			return {kind: 'danger', text: 'Application has been rejected!'};
		else if(appStatus === status.notSubmitted)
			return {kind: 'warning', text: 'Application was returned for resubmission'};
		else if(!appStatus)
			return {kind: 'default', text: 'Application has not been submitted yet'};
		else return {kind: 'danger', text: 'Invalid application status! Please inform Carbon Portal developers about the problem.'};
	}
}

