import Inputs from './FormInputs.jsx';
import ContentPanel from './ContentPanel.jsx';
import {countryCodes, otc} from '../configs.js';

export default function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			var platformTypes = ['', 'VOS', 'ROS', 'FOS'];
			var samplingTypes = ['', 'discrete', 'underway', 'discrete and underway'];
			var platformOpts = _.object(platformTypes, platformTypes);
			var samplingOpts = _.object(samplingTypes, samplingTypes);

			return <Inputs.FormForm>
				<Inputs.Header txt="1. Identification" />
				<Inputs.TextArea {...this.getProps('hasMainPersonnelNamesList')} header="List of the names of the main personnel involved in station operation" />

				<Inputs.DropDownString options={platformOpts} {...this.getProps('hasPlatformType')} header="Platform type" />
				<Inputs.DropDownString options={samplingOpts} {...this.getProps('hasTypeOfSampling')} header="Type of sampling" />
				<Inputs.String {...this.getProps('hasLongName')} header="Platform name" />
				<Inputs.String {...this.getProps('hasShortName')} header="ICES Platform code" />
				<Inputs.DropDownString options={countryCodes} {...this.getProps('hasCountry')} header="Country" />
				<Inputs.String {...this.getProps('hasVesselOwner')} header="Vessel Owner" />

				<Inputs.Longitude {...this.otcProps('hasLon')} header="Longitude [WGS84, decimal degrees]" />
				<Inputs.Latitude {...this.otcProps('hasLat')} header="Latitude [WGS84, decimal degrees]" />

				<Inputs.String {...this.otcProps('hasLocationDescription')} header="Geographical region" />

				<Inputs.Longitude {...this.otcProps('hasWesternmostLon')} header="Westernmost longitude [WGS84, decimal degrees]" />
				<Inputs.Longitude {...this.otcProps('hasEasternmostLon')} header="Easternmost longitude [WGS84, decimal degrees]" />
				<Inputs.Latitude {...this.otcProps('hasNothernmostLat')} header="Northernmost latitude [WGS84, decimal degrees]" />
				<Inputs.Latitude {...this.otcProps('hasSouthernmostLat')} header="Southernmost latitude [WGS84, decimal degrees]" />

				<Inputs.Header txt="2. Measurements and methods" />
				<Inputs.Header txt="2.1 Underway sampling" />

				<Inputs.String {...this.otcProps('hasUnderwayEquilibratorType')} header="Equilibrator type" />
				<Inputs.String {...this.otcProps('hasUnderwayCo2SensorManufacturer')} header="CO2 sensor manufacturer" />
				<Inputs.String {...this.otcProps('hasUnderwayCo2SensorModel')} header="CO2 sensor model" />

				<Inputs.String {...this.otcProps('hasUnderwayOtherSensorManufacturer')} header="Other sensors manufacturer" />
				<Inputs.String {...this.otcProps('hasUnderwayOtherSensorModel')} header="Other sensors model" />
				<Inputs.String {...this.otcProps('hasUnderwayMethodReferences')} header="Method references" />

				<Inputs.String {...this.otcProps('hasUnderwayAdditionalInfo')} header="Additional information such as other parameters measured" />

				<Inputs.Header txt="2.2 Discrete sampling" />

				<Inputs.String {...this.otcProps('hasDiscreteTco2AnalysisMethod')} header="Total CO2: analysis method" />
				<Inputs.String {...this.otcProps('hasDiscreteTco2StandardizationTechnique')} header="Total CO2: standardization technique" />
				<Inputs.String {...this.otcProps('hasDiscreteTco2TechniqueDescription')} header="Total CO2: technique description" />
				<Inputs.String {...this.otcProps('hasDiscreteTco2MethodReferences')} header="Total CO2: method references" />

				<Inputs.String {...this.otcProps('hasDiscreteAlkalinityCurveFitting')} header="Alkalinity: curve fitting method" />
				<Inputs.String {...this.otcProps('hasDiscreteAlkalinityTitrationType')} header="Alkalinity: type of titration" />
				<Inputs.String {...this.otcProps('hasDiscreteAlkalinityOtherTitration')} header="Alkalinity: description of other titration" />
				<Inputs.String {...this.otcProps('hasDiscreteAlkalinityMethodReferences')} header="Alkalinity: method references" />

				<Inputs.String {...this.otcProps('hasDiscretePco2Analysis')} header="pCO2 data analysis" />
				<Inputs.String {...this.otcProps('hasDiscretePco2AnalysisMethod')} header="pCO2 analysis method" />
				<Inputs.String {...this.otcProps('hasDiscretePco2MethodReferences')} header="pCO2 method references" />

				<Inputs.String {...this.otcProps('hasDiscretePhScale')} header="pH scale" />
				<Inputs.String {...this.otcProps('hasDiscretePhAnalysisMethod')} header="pH analysis method (e.g., if electronic, what brand of electrode?)" />
				<Inputs.String {...this.otcProps('hasDiscretePhMethodReferences')} header="pH method references" />

				<Inputs.String {...this.otcProps('hasDiscreteAdditionalInfo')} header="Additional information such as other parameters measured" />

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
		},

		otcProps: function(propName){
			return _.extend(this.getProps(propName), {optional: true});
		},

		getComplexValidationErrors: function(){
			var station = this.state.station;

			var coverageFile = _.findWhere(station.files, {fileType: otc.geoCoverageFileType});

			function hasAllProps(propNames){
				return _.all(propNames, prop => !_.isEmpty(station[prop]));
			}

			let [hasBBox, hasLatLon] = [otc.bboxPropNames, otc.latAndLonPropNames].map(hasAllProps);

			return (coverageFile || hasBBox || hasLatLon)
				? [] : ['Must supply at least one of the following: fixed coordinates, coordinate bounding box, ' +
							`or the '${otc.geoCoverageFileType}' file`];
		}

	});

};

