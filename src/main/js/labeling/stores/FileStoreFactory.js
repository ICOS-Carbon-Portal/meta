module.exports = function(Backend, fileUploadAction, fileDeleteAction){
	return Reflux.createStore({

		init: function(){
			this.listenTo(fileUploadAction, this.fileUploadHandler);  
			this.listenTo(fileDeleteAction, this.fileDeleteHandler);  
		},

		fileUploadHandler: function(fileInfo){
			var self = this;

			var formData = new FormData();
			_.each(_.keys(fileInfo), key => formData.append(key, fileInfo[key]));

			Backend.uploadFile(formData).then(
				() => self.trigger(fileInfo),
				err => console.log(err)
			);
		},

		fileDeleteHandler: function(fileInfo){
			var self = this;

			Backend.deleteFile(_.pick(fileInfo, 'stationUri', 'file')).then(
				() => self.trigger(fileInfo),
				err => console.log(err)
			);
		}

	});
}
