module.exports = function(stationListStore, chooseObjectAction){
	
	return React.createClass({
	
		mixins: [Reflux.connect(stationListStore)],

		render: function(){

			return <ul>{
				this.state.stations.map(function(station){

					var chooseObjectHandler = () => chooseObjectAction(station);
					var isUsers = station.isUsersStation ? 1 : 0;

					var styles = {};
					if (station.chosen) {
						styles.panel_type = 'primary';
						styles.panel_body_show = 'block';
				    
					} else {
						styles.panel_type = 'panel-default';
						styles.panel_body_show = 'none';  
					}					
					
					var panel_body_style = { display: styles.panel_body_show};

					return <li key={station.uri} style={{"list-style-type": "none", "padding-bottom": "10px"}}>
							
						<div className={styles.panel_type + " panel"}>
							<div className="panel-heading" onClick={chooseObjectHandler}> {[station.longName, ' (', station.theme, ')', isUsers].join('')} </div>
							<div className="panel-body" style={panel_body_style}></div>
						</div>
						
					</li>
					
					;
				})

			}</ul>;
		}

	});
}

