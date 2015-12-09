import {status} from '../models/ApplicationStatus.js';

function statusClass(appStatus){
	switch(appStatus){
		case status.acknowledged: return "label-primary";
		case status.rejected: return "label-danger";
		case status.approved: return "label-success";
		case status.submitted: return "label-warning";
		case status.notSubmitted: return "label-default";
	}
}

export default function(stationTypeFiltersAction, appStatusFiltersAction, stationNameFilterAction) {

	return React.createClass({

		getInitialState: function() {
			return {
				stationTypeFilters: localStorage.stationTypeFilters
					? JSON.parse(localStorage.stationTypeFilters)
					: {
						Atmosphere: true,
						Ecosystem: true,
						Ocean: true
				},
				appStatusFilters: localStorage.appStatusFilters
					? JSON.parse(localStorage.appStatusFilters)
					: _.map(status, function(txt, key){
						return {name: key, txt: txt, selected: true};
					}
				),
				stationNameFilter: localStorage.stationNameFilter ? localStorage.stationNameFilter : ""
			};
		},

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
									   checked={self.state.stationTypeFilters.Atmosphere}
									   onChange={() => self.toggleTypeChkBx('Atmosphere')}>
									<span className="glyphicon glyphicon-cloud" style={glyphStyle} aria-hidden="true">
										<span style={spanStyle}>Atmosphere</span>
									</span>
								</input>
							</label>
							<label style={labelStyle} className="label label-info">
								<input type="checkbox"
									   style={inputStyle}
									   checked={self.state.stationTypeFilters.Ecosystem}
									   onChange={() => self.toggleTypeChkBx('Ecosystem')}>
									<span className="glyphicon glyphicon-leaf" style={glyphStyle} aria-hidden="true">
										<span style={spanStyle}>Ecosystem</span>
									</span>
								</input>
							</label>
							<label style={labelStyle} className="label label-info">
								<input type="checkbox"
									   style={inputStyle}
									   checked={self.state.stationTypeFilters.Ocean}
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
							_.map(self.state.appStatusFilters, function(item, index){

									var css = "label " + statusClass(item.txt);

									return (
									<label key={item.name}
										   style={{float: 'left', height: 20, padding: '0 5px 0 0', margin: '0 15px 10px 0'}}
										   className={css}>
										<input type="checkbox"
											   style={{position:'relative', top: 2, left: 3, margin: '2px 7px 0 0'}}
											   onChange={() => self.toggleStatusChkBx(index)}
											   checked={item.selected}
											   name={item.name}>
											<span style={{fontWeight: 600, fontSize: 12}}>{item.txt}</span>
										</input>
									</label>
									);
								})
						}</div>
					</div>

					<div className="row" style={rowStyle}>
						<div className="col-md-3" style={rowStyle}>
							<div className="input-group">
								<input type="text" className="form-control"
									   onChange={self.stationNameSearch}
									   placeholder="Station name text search"
										value={self.state.stationNameFilter}></input>
								<span className="input-group-btn">
									<button className="btn btn-primary" onClick={self.clearStationName} type="button">Clear</button>
								</span>
							</div>
						</div>
					</div>

				</div>
			);
		},

		toggleTypeChkBx: function(stationType){
			var copy = _.clone(this.state.stationTypeFilters);
			copy[stationType] = !copy[stationType];

			stationTypeFiltersAction({stationTypeFilters: copy});
			this.setState({stationTypeFilters: copy});
		},

		toggleStatusChkBx: function(index){
			var old = this.state.appStatusFilters[index];
			var clicked = _.extend({}, old, {selected: !old.selected});
			var copy = _.clone(this.state.appStatusFilters);
			copy[index] = clicked;

			appStatusFiltersAction({appStatusFilters: copy});
			this.setState({appStatusFilters: copy});
		},

		stationNameSearch: function(event){
			var value = event.target.value;
			stationNameFilterAction({stationNameFilter: value});
			this.setState({stationNameFilter: value});
		},

		clearStationName: function(){
			stationNameFilterAction({stationNameFilter: ""});
			this.setState({stationNameFilter: ""});
		}
	});
};