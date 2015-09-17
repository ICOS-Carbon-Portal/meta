
module.exports = function(typesStore, checkSuffixAction, createIndividualAction){

	return React.createClass({

		mixins: [Reflux.connect(typesStore)],

		render: function(){

			var suffixValid = !!this.state.suffixAvailable;
			var icon = suffixValid ? 'plus' : 'remove';
			var inputStyle = suffixValid ? {} : {backgroundColor: 'pink'};

			return <div className="input-group" style={{marginBottom: '10px'}}>
				<input
					type="text" className="form-control" placeholder="New instance's id"
					style={inputStyle} defaultValue={this.state.candidateUriSuffix}
					onChange={this.changeHandler} ref="newUriSuffInput"
					onKeyDown={this.keyDownHandler}
				/>

				<span className="input-group-addon" onClick={this.clickHandler}>
					<span className={'glyphicon glyphicon-' + icon}></span>
				</span>
			</div>;
		},

		changeHandler: _.debounce(function(){
			var suffix = React.findDOMNode(this.refs.newUriSuffInput).value;
			checkSuffixAction(suffix);
		}, 200),

		clickHandler: function(){
			if(this.state.suffixAvailable)
				createIndividualAction({
					uri: this.state.candidateUri,
					type: this.state.chosen,
					suffix: this.state.candidateUriSuffix
				});
			this.cancelAddition();
		},

		keyDownHandler: function(){
			if(event.keyCode == 27){ //Esc
				this.cancelAddition();
			}else if(event.keyCode == 13){ //Enter
				this.clickHandler();
			}
		},

		cancelAddition: function(){
			var elem = React.findDOMNode(this.refs.newUriSuffInput);
			elem.value = null;
			checkSuffixAction(null);
			this.props.cancelHandler();
		}
	});

};

