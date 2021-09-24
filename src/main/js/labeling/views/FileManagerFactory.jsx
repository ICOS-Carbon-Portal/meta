import FileUploader from './FileUploader.jsx';
import ContentPanel from './ContentPanel.jsx';
import ApplicationStatus from '../models/ApplicationStatus.js';

module.exports = function(FileAwareStationStore, fileUploadAction, fileDeleteAction) {

	return React.createClass({

		mixins: [Reflux.connect(FileAwareStationStore)],

		render: function(){
			let self = this;
			let station = this.state.chosen;
			let status = new ApplicationStatus(station);

			const fileName = encodeURIComponent(this.props.stationLabel) + ".zip";
			const url = `./filepack/${fileName}?stationId=` + encodeURIComponent(station.stationUri);
			var uploaderNeeded = !_.isEmpty(station.fileTypes) && status.mayBeSubmitted;
			if (_.isEmpty(station.files) && !uploaderNeeded) return null;

			return <ContentPanel panelTitle="Uploaded files">
				<table className="table">
					<thead>
						<tr>
							<th>#</th>
							<th>File type</th>
							<th>File name</th>
							<th>Action</th>
						</tr>
					</thead>
					<tbody>
						{station.files.map((file, i) =>
							<tr key={"file_" + i}>
								<th>{i + 1}</th>
								<td>{file.fileType}</td>
								<td><a href={file.file + '/' + file.fileName} target="_blank">{file.fileName}</a></td>
								<td>
									<button type="button" className="btn btn-warning" onClick={self.getFileDeleteHandler(file)} disabled={!status.mayBeSubmitted}>
										<span className="fas fa-trash"/> Delete
									</button>
								</td>
							</tr>
						)}
						{uploaderNeeded ? <FileUploader fileSaver={self.fileSaveHandler} fileTypes={station.fileTypes} /> : null}
					</tbody>
				</table>
				{_.isEmpty(station.files) ? null : <a href={url} download><button type="button" className="btn btn-primary">Fetch all the files as a zip archive</button></a>}
			</ContentPanel>;
		},

		fileSaveHandler: function(fileInfo){
			var station = this.state.chosen;
			var fullInfo = _.extend({stationUri: station.stationUri}, fileInfo);
			fileUploadAction(fullInfo);
		},

		getFileDeleteHandler: function(fileInfo){
			var station = this.state.chosen;

			return function(){
				var fullInfo = _.extend({stationUri: station.stationUri}, fileInfo);
				fileDeleteAction(fullInfo);
			};
		}

	});

};

