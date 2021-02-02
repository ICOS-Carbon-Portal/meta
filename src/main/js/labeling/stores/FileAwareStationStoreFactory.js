import {themeToFiles} from '../configs.js';

function getFileExpectations(fileType, actualCount){
	var missing = fileType.min - actualCount;
	return (missing > 0)
		? [['Must supply ', missing, ' more file(s) of type "', fileType.type, '"'].join('')]
		: [];
}

module.exports = function (Backend, ToasterStore, ChosenStationStore, fileUploadAction, fileDeleteAction){
	return Reflux.createStore({

		getInitialState: function(){
			return this.state;
		},

		init: function () {
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
			this.state = _.extend({}, this.state, { chosen: this.addFileTypes(state.chosen) });
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
				if (_.isEmpty(files)) return;
				
				ToasterStore.showToasterHandler("Uploading file...", "info");

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
						() => {
							uploadFiles(_.rest(files));
							ToasterStore.showToasterHandler("Upload was successful", "success");
						},
						err => ToasterStore.showToasterHandler(err.message)
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
				err => ToasterStore.showToasterHandler(err.message)
			);
		},

		updateFiles: function (newFiles) {
			var newChosen = _.extend({}, this.state.chosen, { files: newFiles });

			this.state = _.extend({}, this.state, { chosen: this.addFileTypes(newChosen) });

			this.trigger(this.state);
		},

		addFileTypes: function(station){

			if(!station) return station;

			var allFileTypes = themeToFiles[station.theme];

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
