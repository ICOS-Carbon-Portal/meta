var Inputs = require('./FormInputs.jsx');

module.exports = function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			return <Inputs.FormForm submissionHandler={_.bind(this.submissionHandler, this)} canSave={this.canSave()}>
				<Inputs.Group title="Latitude, WGS84, 5 decimal degrees"><Inputs.Lat5Dec {...this.getProps('lat')} /></Inputs.Group>
				<Inputs.Group title="Longitude, WGS84, 5 decimal degrees"><Inputs.Lon5Dec {...this.getProps('lon')} /></Inputs.Group>
				<Inputs.Group title="Anemometer arm direction, degrees from north"><Inputs.Direction {...this.getProps('anemometerDir')} /></Inputs.Group>
			</Inputs.FormForm>;
		}

	});

};

