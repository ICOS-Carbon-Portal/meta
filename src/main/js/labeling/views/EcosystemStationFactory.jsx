module.exports = function(FileManager) {

	return React.createClass({

		render: function() {

			var fileManagerProps = _.pick(this.props.station, 'files', 'fileTypes', 'stationUri');

			return <div>
				<FileManager {...fileManagerProps} />
				<button type="submit" name="submit" className="btn btn-primary">Save</button>
			</div>;
		}

	});

};

