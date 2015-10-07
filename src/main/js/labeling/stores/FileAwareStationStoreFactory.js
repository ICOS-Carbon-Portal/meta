var themeToFiles = {
	Atmosphere: [
		{min: 1, max: 20, type: "Photo of the site"},
		{min: 1, max: 1, type: "Station map", tip: "Map centered on the station (at the 1km/3000ft scale)"},
		{min: 1, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ecosystem: [
		{min: 1, max: 1, type: "Station information"},
		{min: 1, max: 1, type: "DEM", tip: "Digital elevation model of an area 3x3 km around the tower"},
		{min: 1, max: 1, type: "High resolution image", tip: "High resolution aerial or satellite color image of an area 3x3 km around the tower"},
		{min: 1, max: 1, type: "Vegetation map", tip: "Vegetation map of the 3x3 km around the tower"},
		{min: 12, max: 12, type: "30-degrees photo", tip: "12 photos (every 30 degrees starting from North) taken from the tower position"},
		{min: 0, max: 4, type: "Looking-down photo", tip: "4 photos from the top of the tower looking down, on the 4 sides, outside the tower"},
		{min: 1, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ocean: [
		{min: 0, max: 20, type: "Any file"},
		{min: 1, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	]
};

function getFileExpectations(fileType, actualCount){
	if(fileType.min <= actualCount) return [];
	var howMany = ((fileType.min === fileType.max) ? 'exactly ' : 'at least ') + fileType.min;
	return [['Must supply ', howMany, ' document(s) of type "', fileType.type, '"'].join('')];
}

module.exports = function(Backend, ChosenStationStore, fileUploadAction, fileDeleteAction){
	return Reflux.createStore({

		getInitialState: function(){
			return this.state;
		},

		init: function(){
			this.state = {
				chosen: {
					files: [],
					fileTypes: [],
					isUsersStation: false
				}
			};

			this.listenTo(ChosenStationStore, this.chosenStationHandler);  
			this.listenTo(fileUploadAction, this.fileUploadHandler);  
			this.listenTo(fileDeleteAction, this.fileDeleteHandler);  
		},

		chosenStationHandler: function(state){
			this.state = {chosen: this.addFileTypes(state.chosen)};
			this.trigger(this.state);
		},

		fileUploadHandler: function(fileInfo){
			var self = this;

			var formData = new FormData();
			_.each(_.keys(fileInfo), key => formData.append(key, fileInfo[key]));

			Backend.uploadFile(formData).then(() =>
				(fileInfo.stationUri === self.state.chosen.stationUri)
					? Backend.getStationFiles(fileInfo.stationUri)
					: Promise.resolve([])
			).then(
				files => {
					if (fileInfo.stationUri !== self.state.chosen.stationUri) return;

					self.updateFiles(files);
				},
				err => console.log(err)
			);
		},

		fileDeleteHandler: function(fileInfo){
			var self = this;

			Backend.deleteFile(_.pick(fileInfo, 'stationUri', 'file')).then(
				() => {
					if(fileInfo.stationUri !== self.state.chosen.stationUri) return;

					var newFiles = _.filter( //removing the deleted file
						self.state.chosen.files,
						file => (file.file !== fileInfo.file)
					);

					self.updateFiles(newFiles);
				},
				err => console.log(err)
			);
		},

		updateFiles: function(newFiles){
			var newChosen = _.extend({}, this.state.chosen, {files: newFiles});

			this.state = {chosen: this.addFileTypes(newChosen)};

			this.trigger(this.state);
		},

		addFileTypes: function(station){

			if(!station) return station;

			var allFileTypes = themeToFiles[station.theme];

			var typeNames = _.pluck(allFileTypes, 'type');
			var byType = _.object(typeNames, allFileTypes);

			var typeCounts = _.chain(station.files || [])
				.groupBy('fileType')
				.mapObject(group => group.length)
				.value();

			function actualCount(typeName){
				return typeCounts[typeName] || 0;
			}

			var availableFileTypes = _.filter(allFileTypes, type => (type.max > actualCount(type.type)));
			var fileExpectations = _.flatten(
				allFileTypes.map(type => getFileExpectations(type, actualCount(type.type)))
			);

			return _.extend({}, station, {
				fileTypes: availableFileTypes,
				fileExpectations: fileExpectations
			});
		}

	});
}
