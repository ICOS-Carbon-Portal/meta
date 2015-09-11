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

		if(state.isDisabled) return null;

		var cssClasses = "glyphicon glyphicon-" + state.glyphicon;
		return <span className={cssClasses} onClick={state.clickHandler}></span>;
	}
});

