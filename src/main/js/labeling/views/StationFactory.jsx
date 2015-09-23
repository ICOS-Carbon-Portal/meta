module.exports = function(saveStationAction) { 

	var Form = React.createClass({
		render: function() {
			return <form role="form" onSubmit={this.props.submissionHandler}>{this.props.children}</form>;
		}
	});

	var Comp = React.createClass({
		render: function() {
			var labelStyle = {width: 100, paddingRight: 4};
			var inputStyle = {width: '80%'};

			return <div className="form-group">
				<label style={labelStyle}>{this.props.title}</label>
				<input type="text" id={this.props.infoProp} defaultValue={this.props.defaultValue} style={inputStyle} />
			</div>;
		}
	});

	return React.createClass({

		render: function() {
			if (this.props.theme === 'Ecosystem')
				return <div>Station labeling support for Ecosystem stations is coming soon. Try the Atmosphere stations in the meanwhile.</div>;
			if (this.props.theme === 'Ocean')
				return <div>Station labeling support for Ocean stations is pending. Try the Atmosphere stations in the meanwhile.</div>;

			var station = this.props.station;

			return <Form submissionHandler={this.getSubmissionHandler(station)}>
				<Comp infoProp="shortName" title="Short name" defaultValue={station.shortName} />
				<Comp infoProp="longName" title="Long name" defaultValue={station.longName} />
				<Comp infoProp="lat" title="Latitude" defaultValue={station.lat} />
				<Comp infoProp="lon" title="Longitude" defaultValue={station.lon} />
				<Comp infoProp="aboveGround" title="Above ground" defaultValue={station.aboveGround} />
				<Comp infoProp="aboveSea" title="Above sea" defaultValue={station.aboveSea} />
				<Comp infoProp="stationClass" title="Station class" defaultValue={station.stationClass} />
				<Comp infoProp="plannedDateStarting" title="Planned date starting" defaultValue={station.plannedDateStarting} />
				<button type="submit" class="btn btn-default">Save</button>
			</Form>;
		},

		getSubmissionHandler: station => event => {
			event.preventDefault();
			var self = this;

			var getVal = id => document.getElementById(id).value;
			var getNumVal = id => Number.parseFloat(getVal(id));

			var stringProps = ['shortName', 'longName', 'aboveGround', 'plannedDateStarting'];
			var stringInfo = _.object(stringProps, stringProps.map(getVal));

			var numProps = ['lat', 'lon', 'aboveSea', 'stationClass'];
			var numInfo = _.object(numProps, numProps.map(getNumVal));

			var stationInfo = _.extend({stationUri: station.stationUri}, stringInfo, numInfo);

console.log(stationInfo);

			saveStationAction(stationInfo);
		}

	});

}

