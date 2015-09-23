module.exports = function(stationListStore, chooseStationAction, saveStationAction){

	var Station = require('./StationFactory.jsx')(saveStationAction);

	return React.createClass({

		mixins: [Reflux.connect(stationListStore)],

		render: function(){
			
			var self = this;

			return <ul>{
				self.state.stations.map(function(station){

					var chooseStationHandler = () => chooseStationAction(station);

					var panelClasses = 'panel ' + (station.chosen ? 'panel-primary' : 'panel-default');
					var panelBodyStyle = {display: (station.chosen ? 'block' : 'none')};

					return <li key={station.stationUri} style={{"list-style-type": "none", "padding-bottom": "10px"}}>

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseStationHandler}>{[station.longName, ' (', station.theme, ') '].join('')}</div>
							<div className="panel-body" style={panelBodyStyle}>
								{ (self.state.chosen && station.chosen) ? <Station station={self.state.chosen} theme={station.theme} /> : null }
							</div>
						</div>

					</li>;
				})

			}</ul>;
		}

	});
}

