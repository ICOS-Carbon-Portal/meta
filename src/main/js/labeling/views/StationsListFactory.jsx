var themeGlyphs = {
	"Atmosphere": 'cloud',
	"Ecosystem": 'leaf',
	"Ocean": 'tint'
};

module.exports = function(stationListStore, chooseStationAction, saveStationAction, fileUploadAction){

	var Station = require('./StationFactory.jsx')(saveStationAction);
	var EcoSystem = require('./EcoSystemFactory.jsx')(fileUploadAction);

	return React.createClass({

		mixins: [Reflux.connect(stationListStore)],

		render: function(){
			
			var self = this;

			return <ul>{
				self.state.stations.map(function(station){

					var chooseStationHandler = () => chooseStationAction(station);

					var panelClasses = 'panel ' + (station.chosen ? 'panel-primary' : 'panel-default');
					var panelBodyStyle = {display: (station.chosen ? 'block' : 'none')};
					var icon = 'glyphicon glyphicon-' + (themeGlyphs[station.theme] || 'question-sign');

					var stationType;
					
					if (station.theme === 'Ecosystem') {
						stationType = <EcoSystem station={self.state.chosen} />;
					} else if (station.theme === 'Ocean') {
						stationType = <Station station={self.state.chosen} />;
					} else {
						stationType = <Station station={self.state.chosen} />;
					}

					return <li key={station.stationUri} style={{"list-style-type": "none", "padding-bottom": "10px"}}>

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseStationHandler}>
								<span className={icon}/><span> </span>
								{station.longName}
							</div>
							<div className="panel-body" style={panelBodyStyle}>
								{ (self.state.chosen && station.chosen) ? stationType : null }
							</div>
						</div>

					</li>;
				})

			}</ul>;
		}

	});
}

