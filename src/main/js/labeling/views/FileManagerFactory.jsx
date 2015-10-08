var FileUploader = require('./FileUploader.jsx');
var StationContentPanel = require('./StationContentPanel.jsx');

module.exports = function(FileAwareStationStore, fileUploadAction, fileDeleteAction) {

	return React.createClass({

		mixins: [Reflux.connect(FileAwareStationStore)],

		render: function(){
			var self = this;
			var station = this.state.chosen;

			var uploaderNeeded = (!_.isEmpty(station.fileTypes) && station.isUsersStation);
			if(_.isEmpty(station.files) && !uploaderNeeded) return null;

			return <StationContentPanel panelTitle="Uploaded files">
				<table className="table">
					<thead><tr><th>#</th><th>File type</th><th>File name</th><th>Action</th></tr></thead>
					<tbody>
						{station.files.map((file, i) =>
							<tr key={"file_" + i}>
								<th>{i + 1}</th>
								<td>{file.fileType}</td>
								<td><a href={file.file + '/' + file.fileName} target="_blank">{file.fileName}</a></td>
								<td>
									<button type="button" className="btn btn-warning" onClick={self.getFileDeleteHandler(file)} disabled={!station.isUsersStation}>
										<span className="glyphicon glyphicon-remove"/> Delete
									</button>
								</td>
							</tr>
						)}
						{uploaderNeeded ? <FileUploader fileSaver={self.fileSaveHandler} fileTypes={station.fileTypes}/> : null}
					</tbody>
				</table>
			</StationContentPanel>;
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

