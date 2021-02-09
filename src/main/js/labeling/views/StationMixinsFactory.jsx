import ApplicationStatus from '../models/ApplicationStatus.js';
import ContentPanel from './ContentPanel.jsx';

export default function (FileAwareStationStore, ToasterStore, fileUploadAction, fileDeleteAction, saveStationAction, updateStatusAction) {

	let FileManager = require('./FileManagerFactory.jsx')(FileAwareStationStore, fileUploadAction, fileDeleteAction);
	let LabelingStartWidget = require('./LabelingStartWidgetFactory.jsx')(updateStatusAction);
	let AppStatusWidget = require('./AppStatusWidgetFactory.jsx')(ToasterStore, updateStatusAction);

	var StoreListeningMixin = Reflux.connectFilter(FileAwareStationStore, function(storeState){
		var station = _.clone(storeState.chosen);
		return {
			station: station,
			originalStation: _.clone(station),
			status: new ApplicationStatus(station),
			errors: {},
			formIsValid: true
		};
	})

	var StationBaseMixin = {
		render: function() {
			var station = this.state.station;
			if(!station || !station.stationUri || (station.stationUri !== this.props.stationUri)) return null;

			var complexErrors = this.getComplexValidationErrors ? this.getComplexValidationErrors() : [];

			return <div>

				{this.getPrologue ? this.getPrologue() : null}

				<ContentPanel panelTitle="Station properties">
					{this.getForm()}

					{this.maySave() ? <button type="button" className="btn btn-primary" disabled={!this.canSave()} onClick={this.save}>Save</button> : null}
				</ContentPanel>

				<FileManager stationLabel={this.props.stationLabel}/>

				<LabelingStartWidget formIsValid={this.state.formIsValid} station={this.state.station}
					status={this.state.status} isSaved={this.isUnchanged()} complexErrors={complexErrors} />

				<AppStatusWidget status={this.state.status} />

			</div>;
		},

		componentWillUpdate: function(){
			this.fastState = undefined;
		},

		getUpdater: function(propName){
			var self = this;

			return (errors, newValue) => {
				var newState;
				self.fastState = self.fastState || _.pick(self.state, 'station', 'errors', 'formIsValid');
				var fastState = self.fastState;

				if(!_.isEqual(errors, fastState.errors[propName] || [])){
					newState = newState || {};
					newState.errors = _.clone(fastState.errors);
					newState.errors[propName] = errors;
					newState.formIsValid = _.isEmpty(_.flatten(_.values(newState.errors)));
				}

				if(newValue !== fastState.station[propName]){
					newState = newState || {};
					newState.station = _.clone(fastState.station);
					newState.station[propName] = newValue;
				}

				if(newState) {
					_.extend(fastState, newState);
					self.setState(newState);
				}
			};
		},

		getProps: function(propName){
			return {
				updater: this.getUpdater(propName),
				value: this.state.station[propName],
				disabled: !this.state.status.mayBeSubmitted
			};
		},

		isUnchanged: function(){
			return _.isEqual(this.state.station, this.state.originalStation);
		},

		canSave: function(){
			return this.maySave() && !this.isUnchanged() && this.state.formIsValid;
		},

		maySave: function(){
			return this.state.status.mayBeSubmitted;
		},

		save: function(event) {
			event.preventDefault();
			saveStationAction(this.state.station);
		}

	};

	return [StationBaseMixin, StoreListeningMixin];

}

