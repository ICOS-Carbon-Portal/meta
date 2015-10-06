module.exports = function(FileManager) {

	return React.createClass({

		render: function() {

			return <div>
				<FileManager station={this.props.station} />
				<button type="submit" name="submit" className="btn btn-primary">Save</button>
			</div>;
		}

	});

};

