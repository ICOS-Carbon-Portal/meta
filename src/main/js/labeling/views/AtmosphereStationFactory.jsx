var Inputs = require('./FormInputs.jsx');

module.exports = function(StationMixin) {

	return React.createClass({

		mixins: [StationMixin],

		getForm: function() {
			var stationClassOptions = _.extend({
				options: {
					"1": "1",
					"2": "2",
					"3": "3",
					"Ass": "Ass"
				}
			}, this.getProps('stationClass'));

			return <Inputs.FormForm submissionHandler={_.bind(this.submissionHandler, this)} canSave={this.canSave()}>
				<Inputs.Header title="Identification"></Inputs.Header>
				<Inputs.Group title="Short name"><Inputs.String {...this.getProps('shortName')} /></Inputs.Group>
				<Inputs.Group title="Long name"><Inputs.String {...this.getProps('longName')} /></Inputs.Group>
				<Inputs.Group title="Postal address"><Inputs.TextArea {...this.getProps('POSTAL_ADDRESS')} /></Inputs.Group>
				<Inputs.Group title="Web site"><Inputs.URL {...this.getProps('WEB_SITE')} /></Inputs.Group>
				<Inputs.Group title="Station class"><Inputs.DropDownString {...stationClassOptions} /></Inputs.Group>

				<Inputs.Header title="Station Localisation"></Inputs.Header>
				<Inputs.Group title="Latitude"><Inputs.Latitude {...this.getProps('lat')} /></Inputs.Group>
				<Inputs.Group title="Longitude"><Inputs.Longitude {...this.getProps('lon')} /></Inputs.Group>
				<Inputs.Group title="Above ground"><Inputs.String {...this.getProps('aboveGround')} /></Inputs.Group>
				<Inputs.Group title="Above sea"><Inputs.Number {...this.getProps('aboveSea')} /></Inputs.Group>
				<Inputs.Group title="Accessibility description"><Inputs.TextArea {...this.getProps('ACCESSIBILITY_DESCRIPTION')} /></Inputs.Group>

				<Inputs.Header title="Station Geographical Description"></Inputs.Header>
				<Inputs.Group title="Surrounding vegetation description"><Inputs.TextArea {...this.getProps('SURROUNDING_VEG_DESC')} /></Inputs.Group>
				<Inputs.Group title="Anthropogenic density (population, closest cities, roads …)"><Inputs.TextArea {...this.getProps('ANTHROPOGENIC_DENSITY')} /></Inputs.Group>

				<Inputs.Header title="Construction/Equipment"></Inputs.Header>
				<Inputs.Group title="Planned date starting construction/equipment"><Inputs.String {...this.getProps('plannedDateStarting')} /></Inputs.Group>
				<Inputs.Group title="Planned date ending construction/equipment"><Inputs.StringRequired {...this.getProps('PLANNED_DATE_ENDING_CONSTR')} /></Inputs.Group>
				<Inputs.Group title="Planned date starting measurements"><Inputs.String {...this.getProps('PLANNED_DATE_STARTING_MEASUREMENT')} /></Inputs.Group>
				<Inputs.Group title="Available telecommunication means and reliability"><Inputs.TextAreaRequired {...this.getProps('AVAIL_TELECOM')} /></Inputs.Group>
				<Inputs.Group title="Existing infrastructure"><Inputs.String {...this.getProps('EXISTING_INFRASTRUCTURE')} /></Inputs.Group>

			</Inputs.FormForm>;
		}

	});

}

