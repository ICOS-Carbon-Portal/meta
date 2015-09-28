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

	var File = React.createClass({
		
		render: function() {
			var style = {paddingBottom: '20px'};
			var fileStyle = {border: '1px #333 dotted', width: '100%'};
			var fileInputStyle = {height: '40px'};
			
			return (
				<div  style={style}>
					<div style={fileStyle}>
						<input type="file" name="file" style={fileInputStyle} />
					</div>
				</div>
			);
		}
	});
	

	return React.createClass({
		
		getInitialState: function() {
			return {list: []};
		},
		
		componentDidMount: function() {
			this.state.list.push(<File />);
			this.setState({list: this.state.list});
		},
  		
  		handleClick: function(event) {
 			this.state.list.push(<File />);
			this.setState({list: this.state.list}); 		
  		},
  		
		render: function() {
			var addFileStyle = {float: 'right'};
			var submitStyle = {marginTop: '20px', float: 'left'};
			
			return (
				<div>
			  		<div style={addFileStyle}><button type="button" name="add_file" className="btn btn-primary" onClick={this.handleClick}>Add document</button></div>
					<br /><br />
					<Form submissionHandler={this.submissionHandler} list={this.state.list}>
						<div style={submitStyle}><button type="submit" name="submit" className="btn btn-primary">Save</button></div>
					</Form>
					
				</div>
			);
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

