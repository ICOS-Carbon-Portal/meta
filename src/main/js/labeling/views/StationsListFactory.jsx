var themeGlyphs = {
	Atmosphere: 'cloud',
	Ecosystem: 'leaf',
	Ocean: 'tint'
};

module.exports = function(StationAuthStore, themeToStation, chooseStationAction){

	return React.createClass({

		mixins: [Reflux.connect(StationAuthStore)],

		render: function(){

			var self = this;

			return <ul className="list-group">{
				_.map(self.state.stations, function(station){

					var panelClasses = 'panel panel-' + (station.chosen
						? (station.isUsersStation ? 'primary' : 'warning')
						: (station.isUsersStation ? 'info' : 'default'));

					var icon = 'glyphicon glyphicon-' + (themeGlyphs[station.theme] || 'question-sign');

					var Station = themeToStation[station.theme];

					return <li key={station.stationUri} ref={station.chosen ? "chosenStation" : null} className="list-group-item">

						<div className={panelClasses}>
							<div className="cp-lnk panel-heading" onClick={() => chooseStationAction(station)}>
								<span className={icon}/><span> </span>
								{station.longName}
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

