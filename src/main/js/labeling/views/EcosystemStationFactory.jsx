import Inputs from './FormInputs.jsx';
import {etc} from '../configs.js';

export default function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			return <Inputs.FormForm>
				<Inputs.Lat5Dec {...this.getProps('hasLat')} header="Latitude [WGS84, decimal degrees with 5 decimals]" />
				<Inputs.Lon5Dec {...this.getProps('hasLon')} header="Longitude [WGS84, decimal degrees with 5 decimals]" />
				<Inputs.String {...this.getProps('hasStationClass')} header="Station class" disabled={true} />
				<Inputs.Direction {...this.getProps('hasAnemometerDirection')} header="Anemometer arm direction, degrees from north" />
				<Inputs.NonNegative {...this.getProps('hasEddyHeight')} header="Height of the eddy covariance system, meters above ground" />
				<Inputs.CheckBox {...this.getProps('hasWindDataInEuropeanDatabase')} optional={true} header="Submitted sufficient wind data to the European Database" />
			</Inputs.FormForm>;
		},

		getComplexValidationErrors: function(){
			var files = this.state.station.files;

			var type1 = _.findWhere(files, {fileType: etc.windDirectionFileType1});
			var type2 = _.findWhere(files, {fileType: etc.windDirectionFileType2});
			var alreadySubmitted = (this.state.station.hasWindDataInEuropeanDatabase === 'true');

			return (type1 || type2 || alreadySubmitted)
				? []
				: ['Must either supply one of the wind-direction types of files, or declare that the data is already submitted'];
		}

	});

};

