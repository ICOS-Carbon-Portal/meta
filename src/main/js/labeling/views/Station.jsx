module.exports = function() { 


	var Form = React.createClass({
		render: function() {
			return <form role="form">{this.props.children}</form>;
		}
	});
	
	var Comp = React.createClass({
		render: function() {
			var labelStyle = {width: 100, paddingRight: 4};
			var inputStyle = {width: '80%'};
			
			return  <div className="form-group">
						<label for={this.props.id} style={labelStyle}>{this.props.title}</label>
						<input type="text" id={this.props.id} value={this.props.value} style={inputStyle} />
					</div>
			;
		}
	});	

	return React.createClass({
		
		render: function() {
			
			if (this.props.theme === 'Atmosphere') {
				var station = this.props.station;
			
				return <Form>
							<Comp id={"comp_" + station.stationUri} title={'Station URI'} value={station.stationUri} />
							<Comp id={"comp_" + station.stationUri} title={'Thematic name'} value={station.thematicName} />
							<Comp id={"comp_" + station.stationUri} title={'Long name'} value={station.longName} />
							<Comp id={"comp_" + station.stationUri} title={'Short name'} value={station.shortName} />
							<Comp id={"comp_" + station.stationUri} title={'Lattitude'} value={station.lat} />
							<Comp id={"comp_" + station.stationUri} title={'Longitude'} value={station.lon} />
							<Comp id={"comp_" + station.stationUri} title={'Above ground'} value={station.aboveGround} />
							<Comp id={"comp_" + station.stationUri} title={'Above sea'} value={station.aboveSea} />
							<Comp id={"comp_" + station.stationUri} title={'Station class'} value={station.stationClass} />
							<Comp id={"comp_" + station.stationUri} title={'Planned date starting'} value={station.plannedDateStarting} />	
							<button type="submit" class="btn btn-default">Submit</button>
						</Form>
				;
			
			} else {
				return <div>Under construction!</div>
			}
		}
	
	});

}



