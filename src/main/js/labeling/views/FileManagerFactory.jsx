var FileUploader = require('./FileUploader.jsx');

module.exports = function(fileUploadAction, fileDeleteAction) {

	return React.createClass({

		render: function(){
			var self = this;
			var station = this.props.station;

			var uploaderNeeded = (!_.isEmpty(station.fileTypes) && station.isUsersStation);
			if(_.isEmpty(station.files) && !uploaderNeeded) return null;

			return <div className="panel panel-default">
					<div className="panel-heading"><h3 className="panel-title">Uploaded files</h3></div>
					<table className="table">
						<thead><tr><th>#</th><th>File type</th><th>File name</th><th>Action</th></tr></thead>
						<tbody>{
							station.files.map((file, i) =>
								<tr>
									<th>{i + 1}</th>
									<td>{file.fileType}</td>
									<td><a href={file.file} target="_blank">{file.fileName}</a></td>
									<td>
										<button type="button" className="btn btn-warning" onClick={self.getFileDeleteHandler(file)} disabled={!station.isUsersStation}>
											<span className="glyphicon glyphicon-remove"/> Delete
										</button>
									</td>
								</tr>
							).concat(uploaderNeeded
								? [<FileUploader fileSaver={self.fileSaveHandler} fileTypes={station.fileTypes}/>]
								: []
							)
						}</tbody>
					</table>
			</div>;
		},

		fileSaveHandler: function(fileInfo){
			var fullInfo = _.extend({stationUri: this.props.station.stationUri}, fileInfo);
			fileUploadAction(fullInfo);
		},

		getFileDeleteHandler: function(fileInfo){
			var station = this.props.station;

			return function(){
				var fullInfo = _.extend({stationUri: station.stationUri}, fileInfo);
				fileDeleteAction(fullInfo);
			};
		}

	});

};

