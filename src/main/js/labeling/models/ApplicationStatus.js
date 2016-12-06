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

const dg = {}, tc = {}, pi = {}, s = status;
const allowedStateTransitions = [ //from, to, role
	[s.submitted,      s.acknowledged, tc],
	[s.acknowledged,   s.notSubmitted, tc],
	[s.acknowledged,   s.approved,     tc],
	[s.acknowledged,   s.rejected,     tc],
	[s.notSubmitted,   s.approved,     tc],
	[s.notSubmitted,   s.rejected,     tc],
	[s.approved,       s.rejected,     tc],
];


function userHasRoleForStation(station, role){
	if(role === pi) return station.isUsersStation;
	if(role === tc) return station.isUsersTcStation;
	if(role === dg) return station.isUsersDgStation;
	return false;
}

export default class ApplicationStatus{

	constructor(station){
		this.station = station;
		this.transitions = [];

		if(!station) return;

		const transitions = _.filter(
			allowedStateTransitions,
			([from, , role]) => from === station.hasApplicationStatus && userHasRoleForStation(station, role)
		);

		this.transitions = _.map(transitions, ([, to]) => to);
	}

	get value(){
		return this.station.hasApplicationStatus || s.neverSubmitted;
	}

	get mayBeSubmitted(){
		return this.station.isUsersStation && (this.value === s.neverSubmitted || this.value === s.notSubmitted);
	}

	get canControlLifecycle(){
		const userHasRole = userHasRoleForStation.bind(null, this.station);
		return _.some([pi, tc, dg], userHasRole);
	}

	stationWithStatus(newStatus){
		return _.extend({}, this.station, {hasApplicationStatus: newStatus});
	}

}

