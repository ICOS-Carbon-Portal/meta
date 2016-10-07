import Inputs from './FormInputs.jsx';
import ContentPanel from './ContentPanel.jsx';

module.exports = function(StationMixins) {

	return React.createClass({

		mixins: StationMixins,

		getForm: function() {
			var stationClassOptions = _.extend({
				options: {
					"1": "1",
					"2": "2",
					"3": "3",
					"Ass": "Ass"
				}
			}, this.getProps('hasStationClass'));

			return <Inputs.FormForm>
				<Inputs.Header txt="Identification" />
				<Inputs.String {...this.getProps('hasLongName')} header="Station full name" />
				<Inputs.String {...this.getProps('hasShortName')}
					header="Station short name (short name trigram should be obtained from the GAWSIS system at http://gaw.empa.ch/gawsis/)" />
				<Inputs.TextArea {...this.getProps('hasMainPersonnelNamesList')} header="List of the names of the main personnel involved in ICOS station operation" />
				<Inputs.String {...this.getProps('hasResponsibleInstitutionName')} header="Institution name responsible for the station"/>
				<Inputs.TextArea {...this.getProps('hasAddress')} header="Postal address" />
				<Inputs.URL {...this.getProps('hasWebsite')} header="Web site" />
				<Inputs.DropDownString {...stationClassOptions} header="Station class" />

				<Inputs.Header txt="Station Localisation" />
				<Inputs.Latitude {...this.getProps('hasLat')} header="Latitude [WGS84, decimal degrees]" />
				<Inputs.Longitude {...this.getProps('hasLon')} header="Longitude [WGS84, decimal degrees]" />
				<Inputs.Number {...this.getProps('hasElevationAboveSea')} header="Station altitude above sea level [m]" />
				<Inputs.SlashSeparatedInts {...this.getProps('hasElevationAboveGround')} header="Inlet height(s) above ground (slash separated) [m]" />
				<Inputs.TextArea {...this.getProps('hasAccessibility')} header="Describe accessibility (relevant for mobile lab for ex)" />

				<Inputs.Header txt="Station Geographical Description" />
				<Inputs.TextArea {...this.getProps('hasVegetation')}
					header="Description of surrounding (e.g. vegetation, structures impeding air flow in a 100 km radius)" />
				<Inputs.TextArea {...this.getProps('hasAnthropogenics')}
					header="Nearby anthropogenic activity (population density, closest cities, roads, etc in a 100 km radius)" />

				<Inputs.Header txt="Construction/Equipment" />
				<Inputs.Date {...this.getProps('hasConstructionStartDate')} optional={true} header="Planned date starting construction/equipment (for existing station leave blank) (YYYY-MM-DD)" />
				<Inputs.Date {...this.getProps('hasConstructionEndDate')} optional={true} header="Planned date ending construction/equipment (for existing station leave blank) (YYYY-MM-DD)" />
				<Inputs.Date {...this.getProps('hasOperationalDateEstimate')} header="Planned date starting ICOS measurements (YYYY-MM-DD)" />
				<Inputs.TextArea {...this.getProps('hasTelecom')} header="Available telecommunication means and its reliability" />
				<Inputs.String {...this.getProps('hasExistingInfrastructure')} header="Existing infrastructure (tall tower, collocated station, …)" />
				<Inputs.TextArea {...this.getProps('hasNameListOfNetworksItBelongsTo')}
					header="Is the station already belonging to an environmental measuring network? If so, please list the names of the networks." />
			</Inputs.FormForm>;
		},

		getPrologue: function(){
			return this.state.station.isUsersStation
				? <ContentPanel panelTitle="Labeling how-to">
					<p><strong>ATTENTION!</strong> You will be able to save the form only when all the form errors are corrected,
					that is, all the required fields are filled in and all the field-specific validations pass.
					Please do not navigate away to another station if you have unsaved changes, or you will lose your work.</p>
					<p>This does not concern file upload. Also, you can naturally do multiple editing sessions before you submit your application.</p>
				</ContentPanel>
				: null;
		}

	});

}
