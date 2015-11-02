
function getValidatingMixin(...validators){
	return {
		componentWillMount: function(){
			this.validators = (this.validators || []).concat(validators);
		}
	};
}

var OptionalyRequiringValidatorMixin = {
	componentWillMount: function(){
		this.validators = (this.validators || []).concat([value =>
			(_.isEmpty(value) && !this.props.optional)
				? ["Required field. It must be filled in."]
				: []
		]);
	}
};

function fromMixins(...mixins){
	var allMixins = mixins.concat([OptionalyRequiringValidatorMixin]);
	return React.createClass({mixins: allMixins});
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

var GroupRow = React.createClass({
	render: function() {
		var className = this.props.required ? "cp-required-header" : "cp-not-required-header";

		return <div className="row">
			<div className="form-group col-md-2">
				<label className={className}>{this.props.header}</label>
			</div>
			<div className="form-group col-md-10">
				{this.props.children}
			</div>
		</div>;
	}
});

var TextInput = _.extend({
	render: function () {
		var errors = this.getErrors(this.props.value);

		var style = _.isEmpty(errors) ? {} : {backgroundColor: "pink"};

		return <GroupRow header={this.props.header} required={!this.props.optional}>
			<input type="text" className="form-control" style={style} onChange={this.changeHandler} title={errors.join('\n')}
				value={this.props.value} disabled={this.props.disabled}/>
			</GroupRow>;
	}
}, InputBase);

var TextArea = _.extend({
	render: function() {
		var errors = this.getErrors(this.props.value);

		var style = _.extend(_.isEmpty(errors) ? {} : {backgroundColor: "pink"}, this.inputStyle);

		return <GroupRow header={this.props.header} required={!this.props.optional}>
			<textarea rows="3" className="form-control" style={style} onChange={this.changeHandler} title={errors.join('\n')}
				value={this.props.value} disabled={this.props.disabled} />
		</GroupRow>;
	}
}, InputBase);

var DropDown = _.extend({
	render: function() {
		return <GroupRow header={this.props.header} required={!this.props.optional}>
			<select className="form-control" value={this.props.value} disabled={this.props.disabled} onChange={this.changeHandler}>{
				_.map(this.props.options, (text, value) =>
					<option value={value} key={value}>{text}</option>
				)
			}</select>
		</GroupRow>;
	}
}, InputBase);

var CheckBox = _.extend({}, InputBase, {
	render: function() {
		return <GroupRow header={this.props.header} required={!this.props.optional}>
			<input type="checkbox" checked={this.props.value === 'true'} disabled={this.props.disabled} onChange={this.changeHandler} />
		</GroupRow>;
	},

	changeHandler: function(event){
		var newValue = event.target.checked.toString();
		this.pushUpdate(newValue);
	}
});

var IsNumber = getValidatingMixin(value =>
	_.isUndefined(value) || _.isNull(value) || (!_.isNaN(parseFloat(value)) && isFinite(value))
		? []
		: ["Not a valid number!"]
);

var IsInt = getValidatingMixin(value =>
	_.isUndefined(value) || _.isNull(value) || (parseInt(value).toString() === value.toString())
		? []
		: ["Not a valid integer!"]
);

var IsValidDate = getValidatingMixin(value =>
	_.isUndefined(value) || _.isNull(value) || value === toIsoStr(value)
		? []
		: [value + " is not a valid ISO 8601 date (YYYY-MM-DD)"]
);

function toIsoStr(dateStr){
	function pad(number) {
		if (number < 10) {
			return '0' + number;
		}
		return number;
	}

	var date = new Date(dateStr);
	return date.getUTCFullYear() + '-' + pad(date.getUTCMonth() + 1) + '-' + pad(date.getUTCDate());
}

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
var Is5Dec = matchesRegex(/\.\d{5}$/, "Must have 5 decimals");
var IsSlashSeparated = matchesRegex(/^(\d+)((\/\d+)*)$/, "Must be slash separated integers");
var IsIsoDateFormat = matchesRegex(/^\d{4}-\d{2}-\d{2}$/, "Must be in ISO 8601 format (YYYY-MM-DD)");

module.exports = {

	Number: fromMixins(TextInput, IsNumber),
	NonNegative: fromMixins(TextInput, IsNumber, hasMinValue(0)),

	Latitude: fromMixins(TextInput, IsNumber, hasMinValue(-90), hasMaxValue(90)),
	Lat5Dec: fromMixins(TextInput, IsNumber, hasMinValue(-90), hasMaxValue(90), Is5Dec),

	Longitude: fromMixins(TextInput, IsNumber, hasMinValue(-180), hasMaxValue(180)),
	Lon5Dec: fromMixins(TextInput, IsNumber, hasMinValue(-180), hasMaxValue(180), Is5Dec),

	SlashSeparatedInts: fromMixins(TextInput, IsSlashSeparated),

	Direction: fromMixins(TextInput, IsInt, hasMinValue(0), hasMaxValue(360)),

	URL: fromMixins(TextInput, IsUrl),
	Phone: fromMixins(TextInput, IsPhone),
	Date: fromMixins(TextInput, IsIsoDateFormat, IsValidDate),

	String: fromMixins(TextInput),

	TextArea: fromMixins(TextArea),

	DropDownString: fromMixins(DropDown),

	CheckBox: fromMixins(CheckBox),

	Header: React.createClass({
		render: function() {
			return <div className="row">
				<div className="col-md-2">&nbsp;</div>
				<div className="col-md-10">
					<h4>{this.props.txt}</h4>
				</div>
			</div>;
		}
	}),

	FormForm: React.createClass({
		render: function(){
			if(_.isEmpty(this.props.children)) return null;

			return <form role="form">
				{this.props.children}
			</form>;
		}
	})
};

