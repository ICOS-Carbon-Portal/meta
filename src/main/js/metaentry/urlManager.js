function pathName(uri) {
	return uri.substring(uri.lastIndexOf('/') + 1);
}

function readPath() {
	var parts = window.location.pathname.split('/').filter(Boolean);
	// pathname: /edit/{ontId}/{typeName?}/{individualName?}
	return {
		typeName: parts[2] ? decodeURIComponent(parts[2]) : null,
		individualName: parts[3] ? decodeURIComponent(parts[3]) : null
	};
}

function updatePath(typeUri, individualUri, replaceCurrent) {
	var parts = window.location.pathname.split('/').filter(Boolean);
	var ontId = parts[1];
	var path = '/edit/' + ontId + '/';
	if(typeUri) path += encodeURIComponent(pathName(typeUri));
	if(typeUri && individualUri) path += '/' + encodeURIComponent(pathName(individualUri));
	if(path !== window.location.pathname) {
		var updateHistory = replaceCurrent ? history.replaceState : history.pushState;
		updateHistory.call(history, null, '', path);
	}
}

function findByPathName(list, name) {
	return list.find(function(item){ return pathName(item.uri) === name; });
}

module.exports = { readPath: readPath, updatePath: updatePath, pathName: pathName, findByPathName: findByPathName };
