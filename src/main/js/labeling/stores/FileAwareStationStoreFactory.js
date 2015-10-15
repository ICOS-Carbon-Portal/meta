var themeToFiles = {
	Atmosphere: [
		{min: 1, max: 20, type: "Photo of the site"},
		{min: 1, max: 1, type: "Station map", tip: "Map centered on the station (at the 1km/3000ft scale)"},
		{min: 1, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ecosystem: [
		{min: 1, max: 1, type: "Basic site information", tip: "Ecosystem type, management, recent disturbances, site description, etc."},
		{min: 1, max: 1, type: "Power availability", tip: "Source and kW available in total"},
		{min: 1, max: 1, type: "Internet connection", tip: "Internet connection, system and capacity (speed, robustness)"},
		{min: 1, max: 1, type: "Other projects", tip: "Sharing of the facility with other initiatives, networks, and projects"},
		{min: 0, max: 1, type: "Wind direction (sectors/months)", tip: "Wind sectors in rows (max 30 degrees width) and months on columns"},
		{min: 0, max: 1, type: "Wind direction (time series)", tip: "Maximum hourly time resolution, one full year, time vs degrees w.r.t. North"},
		{min: 1, max: 3, type: "DEM", tip: "Digital elevation model of an area 3x3 km around the tower"},
		{min: 1, max: 3, type: "High resolution image", tip: "High resolution aerial or satellite color image of an area 3x3 km around the tower"},
		{min: 1, max: 3, type: "Vegetation map", tip: "Vegetation map of the 3x3 km around the tower"},

		{min: 12, max: 12, type: "30-degrees photo outside canopy", tip: "12 photos (every 30 degrees starting from North) taken from the tower position"},
		{min: 0, max: 12, type: "30-degrees photo below canopy", tip: "For forest sites"},
		{min: 0, max: 4, type: "4-direction photo", tip: "Forest sites: from the tower top looking down; non-forest: 4 photos of the tower"},
		{min: 1, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ocean: [
		{min: 1, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	]
};

function getFileExpectations(fileType, actualCount){
	var missing = fileType.min - actualCount;
	return (missing > 0)
		? [['Must supply ', missing, ' more document(s) of type "', fileType.type, '"'].join('')]
		: [];
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

			function theStationIsStillChosen(){
				return (fileInfo.stationUri === self.state.chosen.stationUri);
			}

			function makeFormData(file){
				var formData = new FormData();
				formData.append('fileType', fileInfo.fileType.type);
				formData.append('stationUri', fileInfo.stationUri);
				formData.append('uploadedFile', file);
				return formData;
			}

			function uploadFiles(files){
				if(_.isEmpty(files)) return;

				Backend.uploadFile(makeFormData(_.first(files)))
					.then(() =>
						theStationIsStillChosen()
							? Backend.getStationFiles(fileInfo.stationUri)
							: Promise.resolve([])
					)
					.then(latestFileList => {
						if(theStationIsStillChosen()) self.updateFiles(latestFileList);
					})
					.then(
						() => uploadFiles(_.rest(files)),
						err => console.log(err)
					);
			}

			uploadFiles(fileInfo.files);
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

			var availableFileTypes = allFileTypes
				.filter(type => (type.max > actualCount(type.type)))
				.map(type => {
					var newMax = type.max - actualCount(type.type);
					return _.extend({}, type, {max: newMax});
				});
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
