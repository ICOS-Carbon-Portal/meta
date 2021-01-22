import {statusClass, statusLabel} from './StationsListFactory.jsx';

export default function(StationFilterStore, stationFiltersAction) {

	return React.createClass({
		mixins: [Reflux.connect(StationFilterStore)],

		render: function() {
			var self = this;

			var rowStyle = {marginBottom: 15};
			var labelStyle = {float: 'left', height: 22, padding: '0 10px 0 0', margin: '0 20px 10px 0'};
			var inputStyle = {position:'relative', top: 2, left: 3, margin: '0 7px 0 0'};
			var glyphStyle = {top: 4, fontSize: 16};
			var spanStyle = {position:'relative', top:-2, left:3, fontWeight:700, fontFamily:'"Helvetica Neue",Helvetica,Arial,sans-serif'};

			return (
				<div className="container-fluid">

					<div className="row" style={rowStyle}>
						<div className="col-md-12">
							<label style={labelStyle} className="label label-info">
								<input type="checkbox"
									   style={inputStyle}
									   checked={self.state.stationType.Atmosphere}
									   onChange={() => self.toggleTypeChkBx('Atmosphere')}>
									<span className="glyphicon glyphicon-cloud" style={glyphStyle} aria-hidden="true">
										<span style={spanStyle}>Atmosphere</span>
									</span>
								</input>
							</label>
							<label style={labelStyle} className="label label-info">
								<input type="checkbox"
									   style={inputStyle}
									   checked={self.state.stationType.Ecosystem}
									   onChange={() => self.toggleTypeChkBx('Ecosystem')}>
									<span className="glyphicon glyphicon-leaf" style={glyphStyle} aria-hidden="true">
										<span style={spanStyle}>Ecosystem</span>
									</span>
								</input>
							</label>
							<label style={labelStyle} className="label label-info">
								<input type="checkbox"
									   style={inputStyle}
									   checked={self.state.stationType.Ocean}
									   onChange={() => self.toggleTypeChkBx('Ocean')}>
									<span className="glyphicon glyphicon-tint" style={glyphStyle} aria-hidden="true">
										<span style={spanStyle}>Ocean</span>
									</span>
								</input>
							</label>
						</div>
					</div>

					<div className="row" style={rowStyle}>
						<div className="col-md-12">{
							_.map(self.state.appStatus, (item, index) =>
								<label key={item.value}
									style={{float: 'left', height: 20, padding: '0 5px 0 0', margin: '0 15px 10px 0'}}
									className={statusClass(item.value)}>
									<input type="checkbox"
										style={{position:'relative', top: 2, left: 3, margin: '2px 7px 0 0'}}
										onChange={() => self.toggleStatusChkBx(index)}
										checked={item.selected}
										name={item.value}>
										<span style={{fontWeight: 600, fontSize: 12}}>{statusLabel(item.value)}</span>
									</input>
								</label>
							)
						}</div>
					</div>

					<div className="row" style={rowStyle}>
						<div className="col-md-3" style={rowStyle}>
							<div className="input-group">
								<input type="text" className="form-control"
									onChange={self.stationNameSearch}
									placeholder="Station name text search"
									value={self.state.stationName}>
								</input>
								<span className="input-group-btn">
									<button className="btn btn-primary" onClick={self.clearStationName} type="button">Clear text</button>
								</span>
							</div>
						</div>

						<div className="col-md-1" style={rowStyle}>
							<button className="btn btn-primary" onClick={self.resetAllFilters} type="button">Reset all filters</button>
						</div>

						<div className="col-md-2" style={rowStyle}>
							<button className="btn btn-primary" onClick={self.unselectStatuses} type="button">Unselect all statuses</button>
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
