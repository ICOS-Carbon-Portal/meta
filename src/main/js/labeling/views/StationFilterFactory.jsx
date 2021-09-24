import {statusClass, statusLabel} from './StationsListFactory.jsx';

export default function(StationFilterStore, stationFiltersAction) {

	return React.createClass({
		mixins: [Reflux.connect(StationFilterStore)],

		render: function() {
			var self = this;

			return (
				<div>

					<div className="row mb-3">
						<div className="col-md-12">
							<h5>Theme</h5>
							<div className="form-check form-check-inline">
								<input className="form-check-input" type="checkbox" id="atccheckbox"
									checked={self.state.stationType.Atmosphere}
									onChange={() => self.toggleTypeChkBx('Atmosphere')} />
								<label className="form-check-label" htmlFor="atccheckbox">
									<i className="fas fa-cloud"></i> Atmosphere
								</label>
							</div>
							<div className="form-check form-check-inline">
								<input className="form-check-input" type="checkbox" id="etccheckbox"
									checked={self.state.stationType.Ecosystem}
									onChange={() => self.toggleTypeChkBx('Ecosystem')} />
								<label className="form-check-label" htmlFor="etccheckbox">
									<i className="fas fa-leaf"></i> Ecosystem
								</label>
							</div>
							<div className="form-check form-check-inline">
								<input className="form-check-input" type="checkbox" id="otccheckbox"
									checked={self.state.stationType.Ocean}
									onChange={() => self.toggleTypeChkBx('Ocean')} />
								<label className="form-check-label" htmlFor="otccheckbox">
									<i className="fas fa-tint"></i> Ocean
								</label>
							</div>
						</div>
					</div>

					<div className="row mb-4">
						<h5>Status</h5>
						<div className="col-md-12 fs-5">{
							_.map(self.state.appStatus, (item, index) =>
								<label key={item.value}
									className={statusClass(item.value) + " me-2"}>
									<input type="checkbox"
										className="me-2"
										onChange={() => self.toggleStatusChkBx(index)}
										checked={item.selected}
										name={item.value}>
										<span>{statusLabel(item.value)}</span>
									</input>
								</label>
							)
						}</div>
					</div>

					<div className="d-sm-flex justify-content-between ">
						<div>
							<div className="input-group mb-3">
								<input type="text" className="form-control"
									onChange={self.stationNameSearch}
									placeholder="Station name text search"
									value={self.state.stationName}>
								</input>
								<button className="btn btn-secondary" onClick={self.clearStationName} type="button">Clear text</button>
							</div>
						</div>

						<div >
							<div className="d-flex mb-3">
								<button className="btn btn-secondary me-2" onClick={self.unselectStatuses} type="button">Unselect all statuses</button>
								<button className="btn btn-secondary" onClick={self.resetAllFilters} type="button">Reset all filters</button>
							</div>
						</div>
					</div>

				</div>
			);
		},

		toggleTypeChkBx: function(stationType){
			var copy = _.clone(this.state.stationType);
			copy[stationType] = !copy[stationType];

			stationFiltersAction({stationType: copy});
		},

		toggleStatusChkBx: function(index){
			var old = this.state.appStatus[index];
			var clicked = _.extend({}, old, {selected: !old.selected});
			var copy = _.clone(this.state.appStatus);
			copy[index] = clicked;

			stationFiltersAction({appStatus: copy});
		},

		stationNameSearch: function(event){
			var value = event.target.value.toLowerCase();
			stationFiltersAction({stationName: value});
		},

		clearStationName: function(){
			stationFiltersAction({stationName: ""});
		},

		unselectStatuses: function(){
			stationFiltersAction({unselectStatuses: true});
		},

		resetAllFilters: function(){
			stationFiltersAction({resetAllFilters: true});
		}
	});
};
