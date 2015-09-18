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

function makeErrorReport(request){
	var rep = _.pick(request, 'responseText', 'statusText', 'status');

	if(rep.status == 500 && rep.responseText){
		var responseLines = rep.responseText.split('\n');
		if(responseLines.length > 1){
			rep.message = _.head(responseLines);
			rep.stackTrace = _.tail(responseLines).join('\n');
		}
	}

	rep.message = rep.message || rep.statusText || ("Ajax response status: " + rep.status);
	return rep;
}

module.exports = {
	getJson: getJson,
	postJson: postJson
};

