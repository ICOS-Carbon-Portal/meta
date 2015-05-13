module.exports = function(chooseTypeAction){

	return React.createClass({
		handleClick: function(){
			chooseTypeAction(this.props.uri);
		},
		render: function(){

			var classes = "btn" + (this.props.chosen ? " btn-primary" : " btn-default");

			return <button type="button" className={classes} onClick={this.handleClick}>
				{this.props.displayName}
			</button>;
		}
	});
}