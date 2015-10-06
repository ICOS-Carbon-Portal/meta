module.exports = React.createClass({

	getInitialState: function(){
		return {};
	},

	render: function() {
		return <tr>
			<th><span className="glyphicon glyphicon-upload"/></th>
			<td>
				<select className="form-control" ref="fileType" onChange={this.updateFileInfo}>{
					this.props.fileTypes.map(fileType =>
						<option key={fileType.type} value={fileType.type} title={fileType.tip}>{fileType.type}</option>
					)
				}</select>
			</td>
			<td>
				<input type="file" ref="uploadedFile" className="form-control" onChange={this.updateFileInfo}/>
			</td>
			<td>
				<button type="button" className="btn btn-info" onClick={this.uploadHandler} disabled={this.uploadDisabled()}>
					<span className="glyphicon glyphicon-upload"/> Upload
				</button>
			</td>
		</tr>;
	},

	updateFileInfo: function(){
		this.setState({
			fileType: React.findDOMNode(this.refs.fileType).value,
			uploadedFile: React.findDOMNode(this.refs.uploadedFile).files[0]
		});
	},

	uploadDisabled: function(){
		var fileInfo = this.state;
		return !(fileInfo.fileType && fileInfo.uploadedFile);
	},

	uploadHandler: function(){
		this.props.fileSaver(this.state);
	}
});

