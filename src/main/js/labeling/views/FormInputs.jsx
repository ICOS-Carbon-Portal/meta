
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

var InputBaseMixin = {

	componentWillMount: function(){
		this.validators = this.validators || [];
		this.inputStyle = {width: '80%'};
	},

	getErrors: function(value){
		var firstFailing = _.find(this.validators, validator => !_.isEmpty(validator(value)));
		return firstFailing ? firstFailing(value) : [];
	},

	changeHandler: function(event){
		var newValue = event.target.value;
		var errors = this.getErrors(newValue);
		var finalValue = _.isEmpty(errors) ? this.extractUpdatedValue(newValue) : newValue;
		this.props.updater(errors, finalValue);
	}
};

var TextInputMixin = _.extend({
	render: function() {
		var errors = this.getErrors(this.props.value);

		var style = _.extend(_.isEmpty(errors) ? {} : {backgroundColor: "pink"}, this.inputStyle);

		return <input type="text" onChange={this.changeHandler} title={errors.join('\n')} value={this.props.value} disabled={this.props.disabled} style={style} />;
	}
}, InputBaseMixin);

var DropDownMixin = _.extend({
	render: function() {
		return <select value={this.props.value} disabled={this.props.disabled} onChange={this.changeHandler}>{
			_.mapObject(this.props.options, (value, text) =>
				<option value={value} key={value}>{text}</option>
			)
		}</select>;
	}
}, InputBaseMixin);

var StringInputMixin = {extractUpdatedValue: _.identity};
var NumberInputMixin = {extractUpdatedValue: s => Number.parseFloat(s)};

var IsNumberMixin = getValidatingMixin(value => {
	return (Number.parseFloat(value).toString() === value.toString()) ? [] : ["Not a valid number!"];
});

function hasMinValue(minValue){
	return getValidatingMixin(value => {
		var num = Number.parseFloat(value);
		return num >= minValue ? [] : ["Value must not be less than " + minValue];
	});
}

function hasMaxValue(maxValue){
	return getValidatingMixin(value => {
		var num = Number.parseFloat(value);
		return num <= maxValue ? [] : ["Value must not exceed " + maxValue];
	});
}


module.exports = {

	Number: fromMixins(TextInputMixin, NumberInputMixin, IsNumberMixin),

	Latitude: fromMixins(TextInputMixin, NumberInputMixin, IsNumberMixin, hasMinValue(-90), hasMaxValue(90)),

	Longitude: fromMixins(TextInputMixin, NumberInputMixin, IsNumberMixin, hasMinValue(-180), hasMaxValue(180)),

	String: fromMixins(TextInputMixin, StringInputMixin),

	DropDownString: fromMixins(DropDownMixin, StringInputMixin),

	Group: React.createClass({
		render: function() {
			return <div className="form-group">
				<label style={{width: 150, paddingRight: 4}}>{this.props.title}</label>
				{this.props.children}
			</div>;
		}
	})

};

