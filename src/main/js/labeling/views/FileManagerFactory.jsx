
var FileUploader = React.createClass({
	render: function() {

		if(_.isEmpty(this.props.fileTypes)) return null;

		return <tr>
			<th><span className="glyphicon glyphicon-upload"/></th>
			<td>
				<select className="form-control" ref="fileType">{
					this.props.fileTypes.map(fileType =>
						<option value={fileType}>{fileType}</option>
					)
				}</select>
			</td>
			<td>
				<input type="file" ref="uploadedFile" className="form-control" placeholder="Choose file" />
			</td>
			<td>
				<button type="button" className="btn btn-info" onClick={this.uploadHandler}><span className="glyphicon glyphicon-upload"/> Upload</button>
			</td>
		</tr>;
	},

	uploadHandler: function(event){
		this.props.fileSaver({
			fileType: React.findDOMNode(this.refs.fileType).value,
			uploadedFile: React.findDOMNode(this.refs.uploadedFile).files[0]
		});
	}
});


module.exports = function(fileUploadAction, fileDeleteAction) {

	return React.createClass({

		render: function(){

			if(_.isEmpty(this.props.fileTypes) && _.isEmpty(this.props.files)) return null;

			var self = this;

			return <div className="panel panel-default">
					<div className="panel-heading"><h3 className="panel-title">Uploaded files</h3></div>
					<table className="table">
						<thead><tr><th>#</th><th>File type</th><th>File name</th><th>Action</th></tr></thead>
						<tbody>{
							this.props.files.map((file, i) =>
								<tr>
									<th>{i + 1}</th>
									<td>{file.fileType}</td>
									<td><a href={file.file} target="_blank">{file.fileName}</a></td>
									<td>
										<button type="button" className="btn btn-warning" onClick={self.getFileDeleteHandler(file)}>
											<span className="glyphicon glyphicon-remove"/> Delete
										</button>
									</td>
								</tr>
							).concat([
								<FileUploader fileSaver={this.fileSaveHandler} fileTypes={this.props.fileTypes}/>
							])
						}</tbody>
					</table>
			</div>;
		},

		fileSaveHandler: function(fileInfo){
			var fullInfo = _.extend({stationUri: this.props.stationUri}, fileInfo);
			fileUploadAction(fullInfo);
		},

		getFileDeleteHandler: function(fileInfo){
			var self = this;

			return function(){
				var fullInfo = _.extend({stationUri: self.props.stationUri}, fileInfo);
				fileDeleteAction(fullInfo);
			};
		}

	});

};

