module.exports = function(stationListStore, chooseObjectAction){

	var Station = require('./Station.jsx')();
	
	return React.createClass({
		
		mixins: [Reflux.connect(stationListStore)],

		render: function(){
			
			var self = this;

			return <ul>{
				self.state.stations.map(function(station){

					var chooseObjectHandler = () => chooseObjectAction(station);

					var editStatus = station.isUsersStation ? 'editable' : 'not editable';

					var panelClasses = 'panel ' + (station.chosen ? 'panel-primary' : 'panel-default');
					var panelBodyStyle = {display: (station.chosen ? 'block' : 'none')};

					return <li key={station.stationUri} style={{"list-style-type": "none", "padding-bottom": "10px"}}>

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseObjectHandler}>{[station.longName, ' (', station.theme, ') '].join('')}</div>
							<div className="panel-body" style={panelBodyStyle}>
								{ (self.state.chosen && station.chosen) ? <Station station={self.state.chosen} theme={station.theme} edit={editStatus} /> : null }
							</div>
						</div>

					</li>;
				})

			}</ul>;
		}

	});
}

