var ContentPanel = require('./ContentPanel.jsx');

function getValidatingMixin(){
	var validators = arguments;

	return {
		componentWillMount: function(){
			var self = this;
			self.validators = self.validators || [];
			_.each(validators, validator => self.validators.push(validator));
		}
	};
}

function fromMixins(){
	return React.createClass({mixins: arguments});
}

var InputBase = {

	componentWillMount: function(){
		this.validators = this.validators || [];
	},

	componentDidMount: function(){
		this.pushUpdate(this.props.value);
	},

	componentWillReceiveProps: function(newProps){
		if(this.props.value !== newProps.value) this.pushUpdate(newProps.value);
	},

	getErrors: function(value){
		var firstFailing = _.find(this.validators, validator => !_.isEmpty(validator(value)));
		return firstFailing ? firstFailing(value) : [];
	},

	changeHandler: function(event){
		var newValue = event.target.value;
		this.pushUpdate(newValue);
	},

	pushUpdate: function(newValue){
		var errors = this.getErrors(newValue);
		this.props.updater(errors, newValue);
	}
};

var TextInput = _.extend({
	render: function() {
		var errors = this.getErrors(this.props.value);

		var style = _.isEmpty(errors) ? {} : {backgroundColor: "pink"};

		return <input type="text" className="form-control" style={style} onChange={this.changeHandler} title={errors.join('\n')}
					value={this.props.value} disabled={this.props.disabled} />;
	}
}, InputBase);

var TextArea = _.extend({
	render: function() {
		var errors = this.getErrors(this.props.value);

		var style = _.extend(_.isEmpty(errors) ? {} : {backgroundColor: "pink"}, this.inputStyle);

		return <textarea rows="3" className="form-control" style={style} onChange={this.changeHandler} title={errors.join('\n')}
					  value={this.props.value} disabled={this.props.disabled} />;
	}
}, InputBase);

var DropDown = _.extend({
	render: function() {
		return <select className="form-control" value={this.props.value} disabled={this.props.disabled} onChange={this.changeHandler}>{
			_.map(this.props.options, (text, value) =>
				<option value={value} key={value}>{text}</option>
			)
		}</select>;
	}
}, InputBase);

var IsNumber = getValidatingMixin(value =>
	_.isUndefined(value) || _.isNull(value) || !_.isNaN(parseFloat(value))
		? []
		: ["Not a valid number!"]
);

var IsInt = getValidatingMixin(value =>
	_.isUndefined(value) || _.isNull(value) || (parseInt(value).toString() === value.toString())
		? []
		: ["Not a valid integer!"]
);

var IsRequired = getValidatingMixin(value => {
	return _.isEmpty(value) ? ["Required field. It must be filled in."] : [];
});

function hasMinValue(minValue){
	return getValidatingMixin(value => {
		if(_.isNull(value) || _.isUndefined(value)) return [];
		var num = parseFloat(value);
		return num >= minValue ? [] : ["Value must not be less than " + minValue];
	});
}

function hasMaxValue(maxValue){
	return getValidatingMixin(value => {
		if(_.isNull(value) || _.isUndefined(value)) return [];
		var num = parseFloat(value);
		return num <= maxValue ? [] : ["Value must not exceed " + maxValue];
	});
}

function matchesRegex(regex, errorMessage){
	return getValidatingMixin(value => {
		if(_.isEmpty(value)) return [];
		return regex.test(value) ? [] : [errorMessage];
	});
}

var IsPhone = matchesRegex(/^\+[\d\s]{8,}$/, "Must be a phone number in the international format +XXXXXXXX (spaces allowed)");
var IsUrl = matchesRegex(/^(https?):\/\//i, "The URL must begin with http:// or https://");
var IsLat5Dec = matchesRegex(/^\-?\d{2}\.\d{5}$/, "Must have format [-]XX.xxxxx");
var IsLon5Dec = matchesRegex(/^\-?\d{2,3}\.\d{5}$/, "Must have format [-][X]XX.xxxxx");

module.exports = {

	Number: fromMixins(TextInput, IsNumber),

	Latitude: fromMixins(TextInput, IsNumber, hasMinValue(-90), hasMaxValue(90)),
	Lat5Dec: fromMixins(TextInput, IsLat5Dec, hasMinValue(-90), hasMaxValue(90)),

	Longitude: fromMixins(TextInput, IsNumber, hasMinValue(-180), hasMaxValue(180)),
	Lon5Dec: fromMixins(TextInput, IsLon5Dec, hasMinValue(-180), hasMaxValue(180)),

	Direction: fromMixins(TextInput, IsRequired, IsInt, hasMinValue(0), hasMaxValue(360)),

	URL: fromMixins(TextInput, IsUrl),
	Phone: fromMixins(TextInput, IsPhone),

	String: fromMixins(TextInput),

	StringRequired: fromMixins(TextInput, IsRequired),

	TextArea: fromMixins(TextArea),

	TextAreaRequired: fromMixins(TextArea, IsRequired),

	DropDownString: fromMixins(DropDown),

	Header: React.createClass({
		render: function() {
			return <div className="row">
				<div className="col-md-2">&nbsp;</div>
				<div className="col-md-10">
					<h4>{this.props.title}</h4>
				</div>
			</div>;
		}
	}),

	Group: React.createClass({
		render: function() {
			return <div className="row">
				<div className="form-group col-md-2">
					<label>{this.props.title}</label>
				</div>
				<div className="form-group col-md-10">
					{this.props.children}
				</div>
			</div>;
		}
	}),

	FormForm: React.createClass({
		render: function(){
			if(_.isEmpty(this.props.children)) return null;

			return <ContentPanel panelTitle="Station properties">
				<form role="form" onSubmit={this.props.submissionHandler}>
					{this.props.children}
					<button type="submit" className="btn btn-primary" disabled={!this.props.canSave}>Save</button>
				</form>
			</ContentPanel>;
		}
	})
};

