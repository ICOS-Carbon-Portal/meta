var themeGlyphs = {
	"Atmosphere": 'cloud',
	"Ecosystem": 'leaf',
	"Ocean": 'tint'
};

module.exports = function(stationListStore, chooseStationAction, saveStationAction, fileUploadAction){

	var AtmStation = require('./AtmosphereStationFactory.jsx')(saveStationAction);
	var EcoStation = require('./EcosystemStationFactory.jsx')(fileUploadAction);

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

					var stationDom = (station.theme === 'Ecosystem')
						? <EcoStation station={self.state.chosen} />
						: station.theme === 'Atmosphere'
							? <AtmStation station={self.state.chosen} />
							: <div>Station labeling support for Ocean stations is pending. Try the Atmosphere stations in the meanwhile.</div>;

					return <li key={station.stationUri} style={{"list-style-type": "none", "padding-bottom": "10px"}}>

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseStationHandler}>
								<span className={icon}/><span> </span>
								{station.longName}
							</div>
							<div className="panel-body" style={panelBodyStyle}>
								{ (self.state.chosen && station.chosen) ? stationDom : null }
							</div>
						</div>

					</li>;
				})

			}</ul>;
		}

	});
}

