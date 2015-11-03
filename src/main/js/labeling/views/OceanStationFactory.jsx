import Inputs from './FormInputs.jsx';

export default function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			return <Inputs.FormForm>
				<Inputs.String {...this.getProps('hasLongName')} header="Station full name" />
				<Inputs.String {...this.getProps('hasShortName')} header="Station short name" />
				<Inputs.Latitude {...this.getProps('hasLat')} optional={true} header="Latitude [WGS84, decimal degrees]" />
				<Inputs.Longitude {...this.getProps('hasLon')} optional={true} header="Longitude [WGS84, decimal degrees]" />
				<Inputs.String {...this.getProps('hasStationClass')} header="Station class" disabled={true} />
			</Inputs.FormForm>;
		}

	});

};

