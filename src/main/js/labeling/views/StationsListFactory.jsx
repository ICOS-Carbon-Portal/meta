module.exports = function(stationListStore, chooseObjectAction){
	
	return React.createClass({
	
		mixins: [Reflux.connect(stationListStore)],

		render: function(){

console.log(this.state.chosen);

			return <ul>{
				this.state.stations.map(function(station){

					var chooseObjectHandler = () => chooseObjectAction(station);
					var editStatus = station.isUsersStation ? 'editable' : 'not editable';

					var panelClasses = 'panel ' + (station.chosen ? 'primary' : 'panel-default');
					var panelBodyStyle = {display: (station.chosen ? 'block' : 'none')};

					return <li key={station.uri} style={{"list-style-type": "none", "padding-bottom": "10px"}}>

						<div className={panelClasses}>
							<div className="panel-heading" onClick={chooseObjectHandler}>{[station.longName, ' (', station.theme, ') ', editStatus].join('')}</div>
							<div className="panel-body" style={panelBodyStyle}></div>
						</div>

					</li>;
				})

			}</ul>;
		}

	});
}

