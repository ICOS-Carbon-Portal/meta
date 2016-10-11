import Inputs from './FormInputs.jsx';
import {etc} from '../configs.js';
import ContentPanel from './ContentPanel.jsx';

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

		getPrologue: function(){
			const station = this.state.station;
			const docLink = isAssociatedSite(station) ? etc.step1DocAssStations : etc.step1Doc;

			return station.isUsersStation
				? <ContentPanel panelTitle="Labeling how-to">
					<p>Please see the Ecosystem stations labeling process <a target="_blank" href={docLink}>document</a>.
					Some of the file type descriptions in the &quot;Uploaded files&quot; panel below refer to the paragraphs (§) of the document.
					When uploading files, please follow the file naming conventions specified there.
					</p>
				</ContentPanel>
				: null;
		},

		getComplexValidationErrors: function(){
			const errors = [];
			const station = this.state.station;

			const fileCount = fileType => (station.files || []).reduce(
				(sum, file) => file.fileType === fileType ? sum + 1 : sum,
				0
			);

			function require(fileType){
				if(fileCount(fileType) === 0)  errors.push("File missing: " + fileType);
			}

			if(isAssociatedSite(station)) require(etc.instrumentsFileType);
			else {
				require(etc.powerAvailabilityFileType);
				require(etc.internetConnFileType);
				require(etc.otherProjectsFileType);
				require(etc.demFileType);
				require(etc.highResImageFileType);

				const canopyMissing = 12 - fileCount(etc.photos30DegOutsideFileType);
				if(canopyMissing > 0) errors.push(
					`${canopyMissing} file${canopyMissing == 1 ? '' : 's'} missing: ` + etc.photos30DegOutsideFileType
				);

				const windDirectionFileCount = fileCount(etc.windDirectionFileType1) + fileCount(etc.windDirectionFileType2);
				var alreadySubmitted = (station.hasWindDataInEuropeanDatabase === 'true');

				if (windDirectionFileCount == 0 && !alreadySubmitted) errors.push(
					'Must either supply one of the wind-direction types of files, or declare that the data is already submitted'
				);
			}

			return errors;
		}

	});

};

function isAssociatedSite(station){
	return station.hasStationClass == "Ass";
}

