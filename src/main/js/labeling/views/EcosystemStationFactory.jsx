var Inputs = require('./FormInputs.jsx');

module.exports = function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			return <Inputs.FormForm>
				<Inputs.Lat5Dec {...this.getProps('hasLat')} header="Latitude [WGS84, decimal degrees with 5 decimals]" />
				<Inputs.Lon5Dec {...this.getProps('hasLon')} header="Longitude [WGS84, decimal degrees with 5 decimals]" />
				<Inputs.Direction {...this.getProps('hasAnemometerDirection')} header="Anemometer arm direction, degrees from north" />
			</Inputs.FormForm>;
		},

		getComplexValidationErrors: function(){
			return [];//['Complex validation for Ecosystem stations is under development'];
		}

	});

};

