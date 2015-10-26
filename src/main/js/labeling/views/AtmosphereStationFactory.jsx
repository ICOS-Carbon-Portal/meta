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
				<Inputs.Group title="Short name"><Inputs.String {...this.getProps('hasShortName')} /></Inputs.Group>
				<Inputs.Group title="Long name"><Inputs.String {...this.getProps('hasLongName')} /></Inputs.Group>
				<Inputs.Group title="Postal address"><Inputs.TextArea {...this.getProps('hasAddress')} /></Inputs.Group>
				<Inputs.Group title="Web site"><Inputs.URL {...this.getProps('hasWebsite')} /></Inputs.Group>
				<Inputs.Group title="Station class"><Inputs.DropDownString {...stationClassOptions} /></Inputs.Group>

				<Inputs.Header title="Station Localisation"></Inputs.Header>
				<Inputs.Group title="Latitude"><Inputs.Latitude {...this.getProps('hasLat')} /></Inputs.Group>
				<Inputs.Group title="Longitude"><Inputs.Longitude {...this.getProps('hasLon')} /></Inputs.Group>
				<Inputs.Group title="Above ground"><Inputs.String {...this.getProps('hasElevationAboveGround')} /></Inputs.Group>
				<Inputs.Group title="Above sea"><Inputs.Number {...this.getProps('hasElevationAboveSea')} /></Inputs.Group>
				<Inputs.Group title="Accessibility description"><Inputs.TextArea {...this.getProps('hasAccessibility')} /></Inputs.Group>

				<Inputs.Header title="Station Geographical Description"></Inputs.Header>
				<Inputs.Group title="Surrounding vegetation description"><Inputs.TextArea {...this.getProps('hasVegetation')} /></Inputs.Group>
				<Inputs.Group title="Anthropogenic density (population, closest cities, roads …)"><Inputs.TextArea {...this.getProps('hasAnthropogenics')} /></Inputs.Group>

				<Inputs.Header title="Construction/Equipment"></Inputs.Header>
				<Inputs.Group title="Planned date starting construction/equipment"><Inputs.String {...this.getProps('hasConstructionStartDate')} /></Inputs.Group>
				<Inputs.Group title="Planned date ending construction/equipment"><Inputs.StringRequired {...this.getProps('hasConstructionEndDate')} /></Inputs.Group>
				<Inputs.Group title="Planned date starting measurements"><Inputs.String {...this.getProps('hasOperationalDateEstimate')} /></Inputs.Group>
				<Inputs.Group title="Available telecommunication means and reliability"><Inputs.TextAreaRequired {...this.getProps('hasTelecom')} /></Inputs.Group>
				<Inputs.Group title="Existing infrastructure"><Inputs.String {...this.getProps('hasExistingInfrastructure')} /></Inputs.Group>

			</Inputs.FormForm>;
		}

	});

}

