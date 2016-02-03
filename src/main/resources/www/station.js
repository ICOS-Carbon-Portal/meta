$(function () {
	var queryParams = processQuery(window.location.search);

	if(isNumeric(queryParams.lat) && isNumeric(queryParams.lon)){
		queryParams.lat = parseFloat(queryParams.lat);
		queryParams.lon = parseFloat(queryParams.lon);

		initMap(queryParams);
	}
});

function initMap(queryParams) {
	var topoMapESRI = new ol.layer.Tile({
		tag: "topoMapESRI",
		visible: true,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/arcgis/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}'
		}),
		opacity: 0.8
	});

	var station = getVectorLayer(queryParams);
	station.theme = queryParams.theme;

	var view = new ol.View({
		center: ol.proj.fromLonLat([queryParams.lon, queryParams.lat]),
		zoom: 6
	});

	var map = new ol.Map({
		layers: [
			topoMapESRI,
			station
		],
		target: 'map',
		view: view
	});
}

function getVectorLayer(queryParams){
	var iconStyle = new ol.style.Style({
		image: getIcon(queryParams.theme)
	});

	var mapFeature = new ol.Feature({
		geometry: new ol.geom.Point(ol.proj.fromLonLat([queryParams.lon, queryParams.lat]))
	});

	mapFeature.setStyle(iconStyle);

	return new ol.layer.Vector({
		source: new ol.source.Vector({
			features: [mapFeature]
		})
	});
}

function getIcon(theme){
	var icon = {
		anchor: [0.5, 1],
		opacity: 1
	};

	switch (theme){
		case "AS":
			icon.src = 'https://static.icos-cp.eu/share/stations/icons/as.png';
			break;

		case "ES":
			icon.src = 'https://static.icos-cp.eu/share/stations/icons/es.png';
			break;

		case "OS":
			icon.src = 'https://static.icos-cp.eu/share/stations/icons/os.png';
			break;
	}

	return new ol.style.Icon(icon);
}

function isNumeric(n) {
	return !isNaN(parseFloat(n)) && isFinite(n);
}

function processQuery(paramsEnc) {
	// paramsEnc starts with "?"
	var params = decodeURI(paramsEnc.substring(1));
	var pairsArr = params.split("&");
	var query = {};

	pairsArr.forEach(function (pair) {
		var keyVal = pair.split("=");
		query[keyVal[0]] = keyVal[1];
	});

	return query;
}