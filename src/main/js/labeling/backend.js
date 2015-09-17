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

					} else reject(makeErrorReport(req));
				}
			} catch (e) {
				 reject(e);
			}
		};

		req.send();
	});
}

function postJson(url, payload){
	return new Promise(function(resolve, reject){
		var req = new XMLHttpRequest();
		req.open('POST', url);
		req.setRequestHeader('Content-Type', 'application/json;charset=UTF-8');
		req.onreadystatechange = function(){
			if (req.readyState === 4){
				if(req.status === 200) resolve();
				else reject(makeErrorReport(req));
			}
		};

		req.send(JSON.stringify(payload));
	});
}

module.exports = {
	
};

