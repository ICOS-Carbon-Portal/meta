export const status = {
	neverSubmitted: 'NEVER SUBMITTED',
	notSubmitted: 'NOT SUBMITTED',
	submitted: 'SUBMITTED',
	acknowledged: 'ACKNOWLEDGED',
	approved: 'APPROVED',
	rejected: 'REJECTED',
	step2started: 'STEP2STARTED',
	step2approved: 'STEP2APPROVED',
	step2rejected: 'STEP2REJECTED',
	step3approved: 'STEP3APPROVED'
};

export default class ApplicationStatus{

	constructor(station){
		this.station = station;
	}

	get value(){
		return this.station.hasApplicationStatus;
	}

	get mayBeSubmitted(){
		return this.station.isUsersStation && (
			!this.value || this.value === status.notSubmitted
		);
	}

	get canBeAcknowledged(){
		return this.value === status.submitted;
	}

	get canBeReturned(){
		return !!this.value && (this.value !== status.notSubmitted);
	}

	get canBeApproved(){
		return this.canBeReturned && this.value !== status.approved;
	}

	get canBeRejected(){
		return this.canBeReturned && this.value !== status.rejected;
	}

	get step2CanStart(){
		return this.station.isUsersStation && this.value === status.approved;
	}

	get step2CanBeDecided(){
		return this.station.isUsersTcStation && this.value === status.step2started;
	}

	get step3CanBeDecided(){
		return this.station.isUsersDgStation && this.value === status.step2approved;
	}

	get canControlLifecycle(){
		return this.station.isUsersStation || this.station.isUsersTcStation || this.station.isUsersDgStation;
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

	getStep2Started(){
		this.assert(this.step2CanStart, 'Cannot start Step 2!');
		return this.withStatus(status.step2started);
	}

	getStep2Approved(){
		this.assert(this.step2CanBeDecided, 'Cannot approve Step 2!');
		return this.withStatus(status.step2approved);
	}

	getStep2Rejected(){
		this.assert(this.step2CanBeDecided, 'Cannot reject Step 2!');
		return this.withStatus(status.step2rejected);
	}

	getStep3Approved(){
		this.assert(this.step3CanBeDecided, 'Cannot approve Step 3!');
		return this.withStatus(status.step3approved);
	}

	withStatus(newStatus){
		return _.extend({}, this.station, {hasApplicationStatus: newStatus});
	}

	assert(condition, errorMessage){
		if(!condition) throw new Error(errorMessage);
	}

}

