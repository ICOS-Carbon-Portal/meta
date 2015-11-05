import Inputs from './FormInputs.jsx';
import ContentPanel from './ContentPanel.jsx';
import {countryCodes} from '../configs.js';

export default function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			var platformTypes = ['VOS', 'ROS', 'FOS'];
			var samplingTypes = ['discrete', 'underway', 'discrete and underway'];
			var platformOpts = _.object(platformTypes, platformTypes);
			var samplingOpts = _.object(samplingTypes, samplingTypes);
			var countryOpts = _.object(countryCodes, countryCodes);

			return <Inputs.FormForm>
				<Inputs.Header txt="1. Identification" />
				<Inputs.String {...this.getProps('hasMainPersonnelNamesList')} header="List of the names of the main personnel involved in station operation" />

				<Inputs.DropDownString options={platformOpts} {...this.getProps('hasPlatformType')} header="Platform type" />
				<Inputs.DropDownString options={samplingOpts} {...this.getProps('hasTypeOfSampling')} header="Type of sampling" />

				<Inputs.String {...this.getProps('hasLongName')} header="Platform name" />
				<Inputs.String {...this.getProps('hasShortName')} header="ICES Platform code" />

				<Inputs.DropDownString options={countryOpts} {...this.getProps('hasCountry')} header="Country" />
				<Inputs.String {...this.getProps('hasVesselOwner')} header="Vessel Owner" />

				<Inputs.Longitude {...this.getProps('hasLon')} optional={true} header="Longitude [WGS84, decimal degrees]" />
				<Inputs.Latitude {...this.getProps('hasLat')} optional={true} header="Latitude [WGS84, decimal degrees]" />

				<Inputs.String {...this.getProps('hasLocationDescription')} optional={true} header="Geographical region" />

				<Inputs.Longitude {...this.getProps('hasWesternmostLon')} optional={true} header="Westernmost longitude [WGS84, decimal degrees]" />
				<Inputs.Longitude {...this.getProps('hasEasternmostLon')} optional={true} header="Easternmost longitude [WGS84, decimal degrees]" />
				<Inputs.Latitude {...this.getProps('hasNothernmostLat')} optional={true} header="Nothernmost latitude [WGS84, decimal degrees]" />
				<Inputs.Latitude {...this.getProps('hasSouthernmostLat')} optional={true} header="Southernmost latitude [WGS84, decimal degrees]" />

				<Inputs.Header txt="2. Measurements and methods" />
				<Inputs.Header txt="2.1 Underway sampling" />
				<Inputs.Header txt="2.2 Discrete sampling" />
			</Inputs.FormForm>;
		},

		getPrologue: function(){
			return this.state.station.isUsersStation
				? <ContentPanel panelTitle="Labeling how-to">
					<p>For continuous pCO2 on Voluntary Observing Ships, please complete 2.1,
					for discrete please complete 2.2 and for Fixed Time Series Stations please 2.1 
					for continuous measurements at site and 2.2 for discrete measurements at site as applicable.</p>
				</ContentPanel>
				: null;
		}

	});

};

