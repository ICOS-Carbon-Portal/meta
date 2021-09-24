function fileListToArray(fileList){
	return _.range(fileList.length).map(i => fileList[i]);
}

function stateFromProps(props, filesInState = null){
	return {
		fileType: props.fileTypes[0],
		files: filesInState
	};
}

module.exports = React.createClass({

	getInitialState: function(){
		return stateFromProps(this.props);
	},

	componentWillReceiveProps: function(newProps){
		this.setState(stateFromProps(newProps, this.state.files));
	},

	render: function() {
		var multifile = this.state.fileType.max > 1;
		var fileTypeIndex = _.indexOf(this.props.fileTypes, this.state.fileType);
		var uploadDisabled = _.isEmpty(this.state.files);
		var filesLabel = 'Pick file' + (multifile ? 's' : '');
		var tip = uploadDisabled ? this.state.fileType.tip : `Picked ${this.state.files.length} file(s)`;

		return <tr>
			<th><span className="fas fa-upload"/></th>
			<td style={{width: '20%'}}>
				<select className="form-control" ref="fileType" value={fileTypeIndex} onChange={this.updateFileInfo}>{
					_.map(this.props.fileTypes, (fileType, i) =>
						<option key={fileType.type} value={i} title={fileType.tip}>{fileType.type}</option>
					)
				}</select>
			</td>
			<td>
				<input type="file" multiple={multifile} ref="uploadedFile" onChange={this.updateFileInfo} style={{display: 'none'}} />
				<div className="input-group">
					<button className="btn btn-secondary" type="button" onClick={this.filePickHandler}>{filesLabel}</button>
					<input type="text" className="form-control" value={tip} readonly />
				</div>
			</td>
			<td>
				<button type="button" className="btn btn-secondary" onClick={this.uploadHandler} disabled={uploadDisabled}>
					<span className="fas fa-upload"/> Upload
				</button>
			</td>
		</tr>;
	},

	filePickHandler: function(){
		React.findDOMNode(this.refs.uploadedFile).click();
	},

	updateFileInfo: function(){
		var fileTypeIndex = React.findDOMNode(this.refs.fileType).value;
		var fileType = this.props.fileTypes[fileTypeIndex];

		var fileList = React.findDOMNode(this.refs.uploadedFile).files;
		var files = fileListToArray(fileList).slice(0, fileType.max);

		this.setState({
			fileType: fileType,
			files: _.isEmpty(files) ? null : files
		});
	},

	uploadHandler: function(){
		this.props.fileSaver(this.state);
		React.findDOMNode(this.refs.uploadedFile).value = null;
		this.setState(stateFromProps(this.props));
	}
});

