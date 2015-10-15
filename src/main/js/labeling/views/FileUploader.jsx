function fileListToArray(fileList){
	return _.range(fileList.length).map(i => fileList[i]);
}

function stateFromProps(props){
	return {
		fileType: props.fileTypes[0],
		files: null
	};
}

module.exports = React.createClass({

	getInitialState: function(){
		return stateFromProps(this.props);
	},

	componentWillReceiveProps: function(newProps){
		this.setState(stateFromProps(newProps));
	},

	render: function() {
		var multifile = this.state.fileType.max > 1;
		var fileTypeIndex = _.indexOf(this.props.fileTypes, this.state.fileType);
		var uploadDisabled = _.isEmpty(this.state.files);
		var filesLabel = 'Pick file' + (multifile ? 's' : '');
		var tip = uploadDisabled ? this.state.fileType.tip : `Picked ${this.state.files.length} file(s)`;

		return <tr>
			<th><span className="glyphicon glyphicon-upload"/></th>
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
					<span className="input-group-btn">
						<button className="btn btn-info" type="button" onClick={this.filePickHandler}>{filesLabel}</button>
					</span>
					<input type="text" className="form-control" value={tip} readonly />
				</div>
			</td>
			<td>
				<button type="button" className="btn btn-info" onClick={this.uploadHandler} disabled={uploadDisabled}>
					<span className="glyphicon glyphicon-upload"/> Upload
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
	}
});

