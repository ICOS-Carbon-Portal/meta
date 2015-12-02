import {themeGlyphs} from '../configs.js';
import {status} from '../models/ApplicationStatus.js';

function panelClass(station){
	var statusString = `${!!station.chosen} ${!!station.isUsersStation} ${!!station.isUsersNeedingActionStation}`;
	switch (statusString){
		case 'false false false': return 'default';
		case 'false false true': return 'warning';
		case 'false true false': return 'info';
		case 'false true true': return 'warning';
		case 'true false false': return 'warning';
		case 'true false true': return 'danger';
		case 'true true false': return 'primary';
		case 'true true true': return 'danger';
	}
}

function statusClass(appStatus){
	switch(appStatus){
		case status.acknowledged: return "label label-warning";
		case status.rejected: return "label label-danger";
		case status.approved: return "label label-success";
		case status.submitted: return "label label-primary";
		case status.notSubmitted: return "label label-default";
	}
}

export default function(StationAuthStore, themeToStation, chooseStationAction){

	return React.createClass({

		mixins: [Reflux.connect(StationAuthStore)],

		render: function(){

			var self = this;

			return <ul className="list-unstyled">{
				_.map(self.state.stations, function(station){

					var panelClasses = 'panel panel-' + panelClass(station);

					var icon = 'glyphicon glyphicon-' + (themeGlyphs[station.theme] || 'question-sign');

					var Station = themeToStation[station.theme];

					var applicationStatus = "";
					var applicationStatusCSS = "";

					if (station.hasApplicationStatus !== undefined){
						applicationStatus = "Status: " + station.hasApplicationStatus;
						applicationStatusCSS = statusClass(station.hasApplicationStatus);
					}

					return <li key={station.stationUri} ref={station.chosen ? "chosenStation" : null}>

						<div className={panelClasses} style={{marginLeft: 5, marginRight: 5}}>
							<div className="cp-lnk panel-heading" onClick={() => chooseStationAction(station)}>
								<span className={icon}/>
								&nbsp;{station.hasLongName}
								&nbsp;<span style={{ float: 'right' }} className={applicationStatusCSS}>{applicationStatus}</span>
							</div>
							{ station.chosen ? <div className="panel-body"><Station stationUri={station.stationUri}/></div> : null }
						</div>

					</li>;
				})

			}</ul>;
		},

		componentDidUpdate: function(){
			var elem = React.findDOMNode(this.refs.chosenStation);
			if(!elem) return;
			if(elem.getBoundingClientRect().top < 0) elem.scrollIntoView();
		}
	});
}

