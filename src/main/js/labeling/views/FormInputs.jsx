
function getValidatingMixin(){
	var validators = arguments;

	return {
		componentWillMount: function(){
			if(this.props.required) {
				var self = this;
				self.validators = self.validators || [];
				_.each(validators, validator => self.validators.push(validator));
			}
		}
	};
}

function getValidatingMixinIfRequired(){
	var validators = arguments;

	return {
		componentWillMount: function(){
			if(this.props.required) {
				var self = this;
				self.validators = self.validators || [];
				_.each(validators, validator => self.validators.push(validator));
			}
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
		if (this.props.required) { style["border-color"] = "#B94A48"; }

		return <input type="text" className="form-control" style={style} onChange={this.changeHandler} title={errors.join('\n')}
					value={this.props.value} disabled={this.props.disabled} />;
	}
}, InputBase);

var TextArea = _.extend({
	render: function() {
		var errors = this.getErrors(this.props.value);

		var style = _.extend(_.isEmpty(errors) ? {} : {backgroundColor: "pink"}, this.inputStyle);
		if (this.props.required) { style["border-color"] = "#B94A48"; }

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
	_.isUndefined(value) || _.isNull(value) || (!_.isNaN(parseFloat(value)) && isFinite(value))
		? []
		: ["Not a valid number!"]
);

var IsInt = getValidatingMixin(value =>
	_.isUndefined(value) || _.isNull(value) || (parseInt(value).toString() === value.toString())
		? []
		: ["Not a valid integer!"]
);

var IsRequired = getValidatingMixinIfRequired(value => {
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
var Is5Dec = matchesRegex(/\.\d{5}$/, "Must have 5 decimals");
var IsSlashSeparated = matchesRegex(/^(\d+)((\/\d+)*)$/, "Must be slash separated integers");

module.exports = {

	Number: fromMixins(TextInput, IsRequired, IsNumber),
	Latitude: fromMixins(TextInput, IsRequired, IsNumber, hasMinValue(-90), hasMaxValue(90)),
	Lat5Dec: fromMixins(TextInput, IsRequired, IsNumber, hasMinValue(-90), hasMaxValue(90), Is5Dec),

	Longitude: fromMixins(TextInput, IsRequired, IsNumber, hasMinValue(-180), hasMaxValue(180)),
	Lon5Dec: fromMixins(TextInput, IsRequired, IsNumber, hasMinValue(-180), hasMaxValue(180), Is5Dec),

	SlashSeparatedInts: fromMixins(TextInput, IsRequired, IsSlashSeparated),

	Direction: fromMixins(TextInput, IsRequired, IsInt, hasMinValue(0), hasMaxValue(360)),

	URL: fromMixins(TextInput, IsRequired, IsUrl),
	Phone: fromMixins(TextInput, IsRequired, IsPhone),

	String: fromMixins(TextInput, IsRequired),

	TextArea: fromMixins(TextArea, IsRequired),

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

			return <form role="form">
				{this.props.children}
			</form>;
		}
	})
};

