var ContentPanel = require('./ContentPanel.jsx');

var FileExpectations = React.createClass({
	render: function(){
		if(_.isEmpty(this.props.fileExpectations)) return null;

		return <div>
			<p>Please complement your submission with files:</p>
			<ul>{
				this.props.fileExpectations.map(expectation => <li key={expectation}>{expectation}</li>)
			}</ul>
		</div>;
	}
});

var CertifyingClaim = React.createClass({
	render: function(){
		return <div className="input-group" style={{marginTop: 10}}>
			<span className="input-group-addon">
				<input type="checkbox" onChange={this.changeHandler} />
			</span>
			<input type="text" className="form-control" title={this.props.claim} value={this.props.claim} readOnly />
		</div>;
	},

	changeHandler: function(event){
		this.props.changeHandler(event.target.checked);
	}
});

module.exports = function(startLabelingAction) {

	return React.createClass({

		getInitialState: function(){
			return {};
		},

		render: function() {
			var station = this.props.station;

			if(!station.isUsersStation) return null;

			var canStart = _.isEmpty(station.fileExpectations) && this.props.formIsValid && this.props.isSaved && this.state.compliance && this.state.funding;

			return <ContentPanel panelTitle="Submission">

				{this.props.formIsValid ? null : <p>Please correct form errors</p>}
				{this.props.isSaved ? null : <p>Please save the station info before applying</p>}
				<FileExpectations fileExpectations={station.fileExpectations} />

				<CertifyingClaim changeHandler={this.getChangeHandler('compliance')}
					claim={`I certify that the construction/equipments of the station will comply with the "ICOS ${station.theme} Station specifications" document`} />
				<CertifyingClaim changeHandler={this.getChangeHandler('funding')}
					claim="I certify that this station is granted sufficient long term financial resources for its construction and operation" />

				<button type="button" className="btn btn-success" disabled={!canStart} style={{marginTop: 10}}>
					Apply for labeling
				</button>

			</ContentPanel>;
		},

		getChangeHandler(propName){
			var self = this;
			return function(flag){
				var update = {};
				update[propName] = flag;
				self.setState(update);
			};
		}
	});

};

