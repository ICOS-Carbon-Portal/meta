import ContentPanel from './ContentPanel.jsx';
import {status} from '../models/ApplicationStatus';

var Warning = React.createClass({
	render: function(){
		return <p className="text-danger"><strong>{this.props.children}</strong></p>;
	}
});

var FileExpectations = React.createClass({
	render: function(){
		if(_.isEmpty(this.props.fileExpectations)) return null;

		return <div>
			<Warning>Please complement your submission with files:</Warning>
			<p>
				<ul>{
					this.props.fileExpectations.map(expectation => <li className="text-danger" key={expectation}>{expectation}</li>)
				}</ul>
			</p>
		</div>;
	}
});

var CertifyingClaim = React.createClass({
	render: function(){
		return <div className="input-group" style={{marginTop: 10}}>
			<span className="input-group-text">
				<input type="checkbox" onChange={this.changeHandler} />
			</span>
			<input type="text" className="form-control" title={this.props.claim} value={this.props.claim} readOnly />
		</div>;
	},

	changeHandler: function(event){
		this.props.changeHandler(event.target.checked);
	}
});

module.exports = function(statusUpdateAction) {

	return React.createClass({

		getInitialState: function(){
			return {};
		},

		render: function() {
			let station = this.props.station;

			if(!this.props.status.mayBeSubmitted) return null;

			var canStart = _.isEmpty(station.fileExpectations) && _.isEmpty(this.props.complexErrors) && this.props.formIsValid &&
				this.props.isSaved && this.state.compliance && this.state.funding;

			return <ContentPanel panelTitle="Submission">

				{this.props.formIsValid ? null : <Warning>Please correct form errors</Warning>}
				{this.props.isSaved ? null : <Warning>Please save the station properties before applying</Warning>}

				<FileExpectations fileExpectations={station.fileExpectations} />

				{_.map(this.props.complexErrors, (errMsg, i) => <Warning key={'err_' + i}>{errMsg}</Warning>)}

				<CertifyingClaim changeHandler={this.getChangeHandler('compliance')}
					claim={`I certify that the construction/equipments of the station will comply with the "ICOS ${station.theme} Station specifications" document`} />
				<CertifyingClaim changeHandler={this.getChangeHandler('funding')}
					claim="I certify that this station is granted sufficient long term financial resources for its construction and operation" />

				<button type="button" className="btn btn-success" disabled={!canStart} style={{marginTop: 10}} onClick={this.submitHandler}>
					Apply for labeling
				</button>

			</ContentPanel>;
		},

		getChangeHandler: function(propName){
			var self = this;
			return function(flag){
				var update = {};
				update[propName] = flag;
				self.setState(update);
			};
		},

		submitHandler: function(){
			let submittedStation = this.props.status.stationWithStatus(status.submitted);
			statusUpdateAction(submittedStation);
		}

	});

};

