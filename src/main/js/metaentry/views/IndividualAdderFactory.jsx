
module.exports = function(typesStore, checkUriOrSuffixAction, createIndividualAction){

	return React.createClass({

		mixins: [Reflux.connect(typesStore)],

		render: function(){

			var uriValid = this.state.uriAvailable;
			var icon = uriValid ? 'plus' : 'times';
			var inputStyle = uriValid ? {} : {backgroundColor: 'pink'};

			return <div className="input-group" style={{marginBottom: '10px'}}>
				<input
					type="text" className="form-control" placeholder="New instance's id"
					style={inputStyle} onChange={this.changeHandler} ref="newUriOrSuffixInput"
					onKeyDown={this.keyDownHandler}
				/>

				<span className="input-group-text" onClick={this.clickHandler}>
					<span className={'fas fa-' + icon}></span>
				</span>
			</div>;
		},

		changeHandler: function(){
			var uriOrSuffix = React.findDOMNode(this.refs.newUriOrSuffixInput).value;
			checkUriOrSuffixAction(uriOrSuffix);
		},

		clickHandler: function(){
			if(this.state.uriAvailable) createIndividualAction({
				uri: this.state.candidateUri,
				type: this.state.chosen
			})
			this.cancelAddition()
		},

		keyDownHandler: function(event){
			if(event.keyCode == 27){ //Esc
				this.cancelAddition();
			}else if(event.keyCode == 13){ //Enter
				if(this.state.uriAvailable) this.clickHandler();
			}
		},

		cancelAddition: function(){
			var elem = React.findDOMNode(this.refs.newUriOrSuffixInput);
			elem.value = null;
			checkUriOrSuffixAction(null);
			this.props.cancelHandler();
		}
	});

};

