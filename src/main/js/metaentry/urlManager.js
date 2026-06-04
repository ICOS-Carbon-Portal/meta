function localName(uri) {
	return uri.substring(uri.lastIndexOf('/') + 1);
}

function readPath() {
	var parts = window.location.pathname.split('/').filter(Boolean);
	// pathname: /edit/{ontId}/{typeName?}/{individualName?}
	return {
		typeName: parts[2] || null,
		individualName: parts[3] || null
	};
}

function updatePath(typeUri, individualUri) {
	var parts = window.location.pathname.split('/').filter(Boolean);
	var ontId = parts[1];
	var path = '/edit/' + ontId + '/';
	if (typeUri) path += localName(typeUri);
	if (typeUri && individualUri) path += '/' + localName(individualUri);
	history.pushState(null, '', path);
}

module.exports = { readPath: readPath, updatePath: updatePath };
