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

			return <ul className="list-group">{
				self.state.stations.map(function(station){

					var chooseStationHandler = () => chooseStationAction(station);

					var stationIsChosen = (self.state.chosen && station.chosen);

					var panelClasses = 'panel panel-' + (station.chosen
						? (station.isUsersStation ? 'primary' : 'warning')
						: (station.isUsersStation ? 'info' : 'default')
					);
					var icon = 'glyphicon glyphicon-' + (themeGlyphs[station.theme] || 'question-sign');

					var stationDom = () => (station.theme === 'Ecosystem')
						? <EcoStation station={self.state.chosen} />
						: station.theme === 'Atmosphere'
							? <AtmStation station={self.state.chosen} />
							: <div>Station labeling support for Ocean stations is pending. Try the Atmosphere stations in the meanwhile.</div>;

					return <li key={station.stationUri} ref={stationIsChosen ? "chosenStation" : null} className="list-group-item">

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseStationHandler}>
								<span className={icon}/><span> </span>
								{station.longName}
							</div>
							{ stationIsChosen ? <div className="panel-body">{stationDom()}</div> : null }
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

