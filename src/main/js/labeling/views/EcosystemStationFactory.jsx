
var FileUploadPanel = React.createClass({
	render: function(){
		return <div className="panel panel-default">
			<div className="panel-heading">
				<h3 className="panel-title">File upload</h3>
			</div>
			<div className="panel-body">
				<div className="container-fluid">
					<div className="row">
						{this.props.children}
					</div>
				</div>
			</div>
		</div>;
	}
});

module.exports = function(fileUploadAction) { 

	var Form = React.createClass({
		render: function() {
			return ( 
				<form className="" onSubmit={this.props.submissionHandler}>
					{this.props.list.map(function(value){return value;})}
					{this.props.children}
				</form>
			);
		}
	});

	var FileUploader = React.createClass({
		render: function() {
			return <form onSubmit={this.uploadHandler} ref="theForm">
				<FileUploadPanel>
					<div className="col-md-4">
						<div className="input-group">
							<span className="input-group-addon">File type</span>
							<select className="form-control" name="fileType">
								<option value="basic">Basic information</option>
								<option value="satellite">Satellite image</option>
							</select>
						</div>
					</div>
					<div className="col-md-8">
						<div className="input-group">
							<input type="file" name="uploadedFile" className="form-control" placeholder="Choose file ..." />
							<span className="input-group-btn">
								<button className="btn btn-default" type="submit">Upload</button>
							</span>
						</div>
					</div>
				</FileUploadPanel>
			</form>;
		},

		uploadHandler: function(event){
			event.preventDefault();
			var formElem = React.findDOMNode(this.refs.theForm);
			var formData = new FormData(formElem);
			formData.append('stationUri', this.props.stationUri);
			fileUploadAction(formData);
		}
	});

	return React.createClass({
		
		getInitialState: function() {
			return {list: []};
		},

		render: function() {
			var addFileStyle = {float: 'right'};
			var submitStyle = {marginTop: '20px', float: 'left'};

//					<div style={addFileStyle}><button type="button" name="add_file" className="btn btn-primary" onClick={this.handleClick}>Add document</button></div>

			return <div>
				<FileUploader stationUri={this.props.station.stationUri}/>

				<Form submissionHandler={this.submissionHandler} list={this.state.list}>
					<div style={submitStyle}><button type="submit" name="submit" className="btn btn-primary">Save</button></div>
				</Form>

			</div>;
		},

		submissionHandler: function(event) {
			event.preventDefault();

			//document.forms[0].elements[0].files[0]
			
			var files = [];
			files.theme = this.props.station.theme;
			files.stationUri = this.props.station.stationUri;

			for (var i = 0; i < event.target.elements.length; i ++) {
				if (event.target.elements[i].files) {
					files.push( event.target.elements[i].files[0] );
				}
			}

			fileUploadAction(files);

		}
	});

}

