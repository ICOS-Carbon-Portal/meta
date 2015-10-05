var themeGlyphs = {
	"Atmosphere": 'cloud',
	"Ecosystem": 'leaf',
	"Ocean": 'tint'
};

module.exports = function(stationListStore, FileManager, chooseStationAction, saveStationAction){

	var themeToStation = {
		Atmosphere: require('./AtmosphereStationFactory.jsx')(saveStationAction),
		Ecosystem: require('./EcosystemStationFactory.jsx')(FileManager),
		Ocean: require('./OceanStationFactory.jsx')(FileManager)
	};

	return React.createClass({

		mixins: [Reflux.connect(stationListStore)],

		render: function(){

			var self = this;

			return <ul className="list-group">{
				_.map(self.state.stations, function(station){

					var chooseStationHandler = () => chooseStationAction(station);

					var stationIsChosen = (self.state.chosen && station.chosen);

					var panelClasses = 'panel panel-' + (station.chosen
						? (station.isUsersStation ? 'primary' : 'warning')
						: (station.isUsersStation ? 'info' : 'default'));

					var icon = 'glyphicon glyphicon-' + (themeGlyphs[station.theme] || 'question-sign');

					var Station = themeToStation[station.theme];

					return <li key={station.stationUri} ref={stationIsChosen ? "chosenStation" : null} className="list-group-item">

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseStationHandler}>
								<span className={icon}/><span> </span>
								{station.longName}
							</div>
							{ stationIsChosen ? <div className="panel-body"><Station station={self.state.chosen} /></div> : null }
						</div>

					</li>;
				})

			}</ul>;
		},

		componentDidUpdate: function(){
			if(!this.state.chosen) return;
			var elem = React.findDOMNode(this.refs.chosenStation);
			if(elem.getBoundingClientRect().top < 0) elem.scrollIntoView();
		}
	});
}

