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
		var multifile = this.state.fileType && (this.state.fileType.max > 1);
		var fileTypeIndex = _.indexOf(this.props.fileTypes, this.state.fileType);
		var uploadDisabled = _.isEmpty(this.state.files);

		return <tr>
			<th><span className="glyphicon glyphicon-upload"/></th>
			<td>
				<select className="form-control" ref="fileType" value={fileTypeIndex} onChange={this.updateFileInfo}>{
					_.map(this.props.fileTypes, (fileType, i) =>
						<option key={fileType.type} value={i} title={fileType.tip}>{fileType.type}</option>
					)
				}</select>
			</td>
			<td>
				<input type="file" multiple={multifile} ref="uploadedFile" className="form-control" onChange={this.updateFileInfo}/>
			</td>
			<td>
				<button type="button" className="btn btn-info" onClick={this.uploadHandler} disabled={uploadDisabled}>
					<span className="glyphicon glyphicon-upload"/> Upload
				</button>
			</td>
		</tr>;
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

