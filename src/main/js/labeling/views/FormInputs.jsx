
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
	var mixins = [];
	_.each(arguments, argument => mixins.push(argument));
	return React.createClass({mixins: mixins});
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

var StringInputMixin = _.extend({extractUpdatedValue: _.identity}, TextInputMixin);

var NumberInputMixin = _.extend({extractUpdatedValue: s => Number.parseFloat(s)}, TextInputMixin);

var IsNumberMixin = getValidatingMixin(value => {
	return (Number.parseFloat(value).toString() === value.toString()) ? [] : ["Not a valid number!"];
});


module.exports = {

	Number: fromMixins(NumberInputMixin, IsNumberMixin),

	String: fromMixins(StringInputMixin),

	Group: React.createClass({
		render: function() {
			return <div className="form-group">
				<label style={{width: 150, paddingRight: 4}}>{this.props.title}</label>
				{this.props.children}
			</div>;
		}
	})

};

