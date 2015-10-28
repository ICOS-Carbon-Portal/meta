import ApplicationStatus from '../models/ApplicationStatus.js';
import ContentPanel from './ContentPanel.jsx';

export default function(FileAwareStationStore, fileUploadAction, fileDeleteAction, saveStationAction, updateStatusAction) {

	let FileManager = require('./FileManagerFactory.jsx')(FileAwareStationStore, fileUploadAction, fileDeleteAction);
	let LabelingStartWidget = require('./LabelingStartWidgetFactory.jsx')(updateStatusAction);
	let AppStatusWidget = require('./AppStatusWidgetFactory.jsx')(updateStatusAction);

	var StoreListeningMixin = Reflux.connectFilter(FileAwareStationStore, function(storeState){
		var station = _.clone(storeState.chosen);
		return {
			station: station,
			originalStation: _.clone(station),
			status: new ApplicationStatus(station),
			errors: {},
			valid: true
		};
	})

	var StationBaseMixin = {
		render: function() {
			var station = this.state.station;
			if(!station || !station.stationUri || (station.stationUri !== this.props.stationUri)) return null;

			return <div>
				<ContentPanel panelTitle="Station properties">
					{this.getForm()}

					{this.maySave() ? <button type="button" className="btn btn-primary" disabled={!this.canSave()} onClick={this.save}>Save</button> : null}
				</ContentPanel>

				<FileManager />

				<LabelingStartWidget formIsValid={this.state.valid} station={this.state.station} status={this.state.status} isSaved={this.isUnchanged()} />
				<AppStatusWidget status={this.state.status} />

			</div>;
		},

		getUpdater: function(propName){
			var self = this;

			return (errors, newValue) => {
				var newState;

				if(!_.isEqual(errors, self.state.errors[propName] || [])){
					newState = newState || _.clone(self.state);
					newState.errors = _.clone(self.state.errors);
					newState.errors[propName] = errors;
					newState.valid = _.isEmpty(_.flatten(_.values(newState.errors)));
				}

				if(newValue !== self.state.station[propName]){
					newState = newState || _.clone(self.state);
					newState.station = _.clone(self.state.station);
					newState.station[propName] = newValue;
				}

				if(newState) self.setState(newState);
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
			return this.maySave() && !this.isUnchanged() && this.state.valid;
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

