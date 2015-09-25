module.exports = function(Backend, fileUploadAction){
	return Reflux.createStore({

		publishState: function(){
			this.trigger(this.state);
		},
	                          
	    init: function(){
			this.listenTo(fileUploadAction, this.fileUploadHandler);  
	    },
	                          
	    fileUploadHandler: function(files){  
		    console.log('in the store we got..' + files.length + ' items');
		    
		    console.log(files.theme + ' ---- ' + files.stationUri);
		    
		    //Backend.upload(theFileName)
		    
		    var formData = new FormData();
		    formData.append("theme", files.theme);
		    formData.append("stationUri", files.stationUri); 
		    
		    for (var i = 0; i < files.length; i ++) {
			    formData.append("file_" + i, files[i]);
			    console.log(files[i].name);
		    }
		    
		    var request = new XMLHttpRequest();
		    request.open("POST", "http://localhost:9094/labeling/fileupload");
		    request.send(formData);
	    }
	});
}
