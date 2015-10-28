var Inputs = require('./FormInputs.jsx');

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
				<Inputs.Header title="Identification"></Inputs.Header>
				<Inputs.Group title="Station full name"><Inputs.String {...this.getProps('hasLongName')}/></Inputs.Group>
				<Inputs.Group title="Station short name (short name trigram should be obtained form the GAWSIS system at http://gaw.empa.ch/gawsis/)">
					<Inputs.String {...this.getProps('hasShortName')} />
				</Inputs.Group>
				<Inputs.Group title="List of the names of the main personnel involved in station operation">
					<Inputs.TextArea {...this.getProps('HASMAINPERSONEL')} />
				</Inputs.Group>
				<Inputs.Group title="Institution name responsible for the station"><Inputs.String {...this.getProps('HASRESPONSIBLEINSTITUTION')}/></Inputs.Group>
				<Inputs.Group title="Postal address"><Inputs.TextArea {...this.getProps('hasAddress')} /></Inputs.Group>
				<Inputs.Group title="Web site"><Inputs.URL {...this.getProps('hasWebsite')} /></Inputs.Group>
				<Inputs.Group title="Station class"><Inputs.DropDownString {...stationClassOptions} /></Inputs.Group>

				<Inputs.Header title="Station Localisation"></Inputs.Header>
				<Inputs.Group title="Latitude [WGS84, decimal degrees]"><Inputs.Latitude {...this.getProps('hasLat')} /></Inputs.Group>
				<Inputs.Group title="Longitude [WGS84, decimal degrees]"><Inputs.Longitude {...this.getProps('hasLon')} /></Inputs.Group>
				<Inputs.Group title="Height(s) above ground level [m]"><Inputs.String {...this.getProps('hasElevationAboveGround')} /></Inputs.Group>
				<Inputs.Group title="Height of ground above sea level [m]"><Inputs.Number {...this.getProps('hasElevationAboveSea')} /></Inputs.Group>
				<Inputs.Group title="Describe accessibility (relevant for mobile lab for ex)"><Inputs.TextArea {...this.getProps('hasAccessibility')} /></Inputs.Group>

				<Inputs.Header title="Station Geographical Description"></Inputs.Header>
				<Inputs.Group title="Surrounding vegetation description (100 km radius)"><Inputs.TextArea {...this.getProps('hasVegetation')} /></Inputs.Group>
				<Inputs.Group title="Anthropogenic density (100 km radius, population, closest cities, roads …)"><Inputs.TextArea {...this.getProps('hasAnthropogenics')} /></Inputs.Group>

				<Inputs.Header title="Construction/Equipment"></Inputs.Header>
				<Inputs.Group title="Planned date starting construction/equipment"><Inputs.String {...this.getProps('hasConstructionStartDate')} /></Inputs.Group>
				<Inputs.Group title="Planned date ending construction/equipment"><Inputs.String {...this.getProps('hasConstructionEndDate')} /></Inputs.Group>
				<Inputs.Group title="Planned date starting measurements"><Inputs.String {...this.getProps('hasOperationalDateEstimate')} /></Inputs.Group>
				<Inputs.Group title="Available telecommunication means and its reliability"><Inputs.TextArea {...this.getProps('hasTelecom')} /></Inputs.Group>
				<Inputs.Group title="Existing infrastructure (tall tower, collocated station, …)"><Inputs.String {...this.getProps('hasExistingInfrastructure')} /></Inputs.Group>
				<Inputs.Group title="Is the station already belonging to an environmental measuring network? If so, please list the names of the networks.">
					<Inputs.TextArea {...this.getProps('HASNETWORKS', true)} />
				</Inputs.Group>

			</Inputs.FormForm>;
		}

	});

}

