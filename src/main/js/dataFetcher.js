'use strict';

function getJson(url){
	return new Promise(function(resolve, reject){
		var req = new XMLHttpRequest();
		req.open('GET', url);
		req.responseType = 'json';
    req.setRequestHeader('Accept', 'application/json');
		req.onreadystatechange = function(){
      try {
        if (req.readyState === 4) {
          if (req.status < 400 && req.status >= 200) {

            if (req.responseType === 'json')
                resolve(req.response);
            else resolve(JSON.parse(req.responseText || null));

          } else reject(new Error(req.statusText || "Ajax response status: " + req.status));
        }
      } catch (e) {
          reject(e);
      }
    };

    req.send();
	});
}

module.exports = {
	listClasses: function(){
		return getJson('/api/listClasses');
	},
	listIndividuals: function(classUri){
		var url = '/api/listIndividuals?classUri=' + encodeURIComponent(classUri);
		return getJson(url);
	}
};
