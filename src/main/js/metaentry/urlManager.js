function localName(uri) {
	return uri.substring(uri.lastIndexOf('/') + 1);
}

function pathNameFromLocalName(name) {
	return name
		.replace(/([a-z0-9])([A-Z])/g, '$1-$2')
		.replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')
		.replace(/[_\s]+/g, '-')
		.replace(/-+/g, '-')
		.replace(/^-|-$/g, '')
		.toLowerCase();
}

function pathName(uri) {
	return pathNameFromLocalName(localName(uri));
}

function readPath() {
	var parts = window.location.pathname.split('/').filter(Boolean);
	// pathname: /edit/{ontId}/{typeName?}/{individualName?}
	return {
		typeName: parts[2] ? decodeURIComponent(parts[2]) : null,
		individualName: parts[3] ? decodeURIComponent(parts[3]) : null
	};
}

function updatePath(typeUri, individualUri) {
	var parts = window.location.pathname.split('/').filter(Boolean);
	var ontId = parts[1];
	var path = '/edit/' + ontId + '/';
	if (typeUri) path += encodeURIComponent(pathName(typeUri));
	if (typeUri && individualUri) path += '/' + encodeURIComponent(pathName(individualUri));
	history.pushState(null, '', path);
}

module.exports = { readPath: readPath, updatePath: updatePath, pathName: pathName, pathNameFromLocalName: pathNameFromLocalName };
