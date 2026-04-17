/*
The implementation is meant to work for both static and dynamic buttons that can change their enabled/disabled status, icon and click handler.
However, it can also be used as a static button that will never get state updates (only props updates can come when parents are redrawn).
*/

function stateFromProps(props){
	return _.omit(props, 'stateSource');
}

module.exports = React.createClass({

	getInitialState: function(){
		return stateFromProps(this.props);
	},

	componentWillReceiveProps: function(newProps){
		this.setState(stateFromProps(newProps));
	},

	componentWillMount: function(){
		var self = this;
		this.unsubscribe = this.props.stateSource
			? this.props.stateSource.listen(function(stateUpdate){
					self.setState(stateUpdate);
				})
			: _.noop;
	},

	componentWillUnmount: function(){
		this.unsubscribe();
	},

	render: function(){
		var state = this.state;
		var btnVariant = state.btnVariant || "btn-outline-secondary";
		var marginClass = state.noLeftMargin ? "" : " ms-xl-2";
		var buttonClasses = "btn btn-sm text-nowrap flex-shrink-0" + marginClass + " " + btnVariant;

		if(state.label){
			var icon = state.icon ? <span className={"fas fa-" + state.icon + " me-1"}></span> : null;
			return <button
				type="button"
				className={buttonClasses}
				onClick={state.clickHandler}
				disabled={!!state.isDisabled}
			>{icon}{state.label}</button>;
		}

		if(state.asButton){
			return <button
				type="button"
				className={buttonClasses}
				onClick={state.clickHandler}
				disabled={!!state.isDisabled}
			><span className={"fas fa-" + state.icon}></span></button>;
		}

			if(state.isDisabled) return null;

		var cssClasses = "fas fa-" + state.icon;
		return <span className={cssClasses} onClick={state.clickHandler} role="button"></span>;
	}
});
