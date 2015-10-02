module.exports = function(Backend, fileUploadAction){
	return Reflux.createStore({

		init: function(){
			this.listenTo(fileUploadAction, this.fileUploadHandler);  
		},

		fileUploadHandler: function(formData){
			Backend.uploadFile(formData).then(
				() => console.log("Upload successful"),
				err => console.log(err)
			);
		}
	});
}
