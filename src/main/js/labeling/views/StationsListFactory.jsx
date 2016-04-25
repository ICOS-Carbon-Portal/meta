import {themeGlyphs} from '../configs.js';
import {status} from '../models/ApplicationStatus.js';

function panelClass(station){
	var statusString = `${!!station.chosen} ${!!station.isUsersStation} ${!!station.isUsersNeedingActionStation}`;
	switch (statusString){
		case 'false false false': return 'default';
		case 'false false true': return 'warning';
		case 'false true false': return 'info';
		case 'false true true': return 'warning';
		case 'true false false': return 'default';
		case 'true false true': return 'danger';
		case 'true true false': return 'primary';
		case 'true true true': return 'danger';
	}
}

function statusClass(appStatus){
	switch(appStatus){
		case status.acknowledged: return "label label-primary";
		case status.rejected: return "label label-danger";
		case status.approved: return "label label-success";
		case status.submitted: return "label label-warning";
		case status.notSubmitted: return "label label-default";
	}
}

const ShowLazily = React.createClass({

	getInitialState: function(){
		return {show: false};
	},

	componentDidMount: function(){
		var self = this;
		self.timeout = setTimeout(
			() => self.setState({show: true}),
			self.props.delay
		);
	},

	componentWillUnmount: function(){
		clearTimeout(this.timeout);
	},

	render: function(){
		return this.state.show
			? <div>{this.props.children}</div>
			: null;
	}
});

export default function(StationAuthStore, themeToStation, chooseStationAction){

	return React.createClass({

		mixins: [Reflux.connect(StationAuthStore)],

		render: function(){

			var self = this;

			if (self.state.stations.length == 0){
				return <ShowLazily delay={100}>
					<div className="container-fluid">
						<div className="row">
							<div className="col-md-2">
								<h4>
									<span className="label label-danger">No stations match your search criteria</span>
								</h4>
							</div>
						</div>
					</div>
				</ShowLazily>;
			} else {
				return (
					<ul className="list-unstyled">{
						_.map(self.state.stations, function(station){

							var panelClasses = 'panel panel-' + panelClass(station);

							var icon = 'glyphicon glyphicon-' + (themeGlyphs[station.theme] || 'question-sign');

							var Station = themeToStation[station.theme];

							var applicationStatus = station.hasApplicationStatus;
							var applicationStatusCSS = statusClass(station.hasApplicationStatus);

							return <li key={station.stationUri} ref={station.chosen ? "chosenStation" : null}>

								<div className={panelClasses} style={{marginLeft: 5, marginRight: 5}}>
									<div className="cp-lnk panel-heading" onClick={() => chooseStationAction(station)}>
										<span className={icon}/>
										&nbsp;{station.hasLongName}
										&nbsp;<label style={{ float: 'right', height: 20, padding: '2 4' }} className={applicationStatusCSS}>
											<span style={{fontWeight: 600, fontSize: 12, position: 'relative', top: 2}}>{applicationStatus}</span>
										</label>
									</div>
									{ station.chosen ? <div className="panel-body"><Station stationUri={station.stationUri} stationLabel={station.hasLongName}/></div> : null }
								</div>

							</li>;
							})

					}</ul>
				);
			}
		},

		componentDidUpdate: function(){
			var elem = React.findDOMNode(this.refs.chosenStation);
			if(!elem) return;
			if(elem.getBoundingClientRect().top < 0) elem.scrollIntoView();
		}
	});
}

