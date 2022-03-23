import { urlValidator } from "../../models/Validation";

export default React.createClass({

	render: function(){

		var rangeValues = this.props.rangeValues;

		if(rangeValues == null) return null;

		const instruction = _.isEmpty(rangeValues)
			? "Enter URL to be the new value:"
			: "Double-click and choose the new value (type to search), or enter a URL:";

		const saveDisabled = this.state && !this.state.valid;
		const saveButtClass = "btn btn-" + (saveDisabled ? "danger" : "primary");
		const saveButtMsg = saveDisabled ? this.state.errors[0] : "Save";

		return (
			<form>

				<div className="form-group">
					<label className="form-label" for="newObjPropValue">{instruction}</label>
					<div className="input-group mb-2">
						<input
							type="text" className="form-control"
							id="newObjPropValue" list="newPropValueList" ref="valueSelect"
							onChange={v => this.setState(urlValidator(v.target.value))}
						/>
						<button type="button" className="btn btn-secondary" onClick={this.props.cancelAddition}>Cancel</button>
						<button type="button" className={saveButtClass} onClick={this.saveValue} disabled={saveDisabled}>{saveButtMsg}</button>
					</div>
				</div>

				<datalist id="newPropValueList">{
					_.map(rangeValues, function(value){
						return <option key={value.uri} value={value.uri}>{value.displayName}</option>;
					})
				}</datalist>

			</form>
		);
	},

	saveValue: function(){
		let value = React.findDOMNode(this.refs.valueSelect).value;
		this.props.saveValue(value);
	},

});

