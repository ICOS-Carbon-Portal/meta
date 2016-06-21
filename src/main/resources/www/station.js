var queryParams = processQuery(window.location.search);

if (queryParams.theme && queryParams.coverage){
	initMap(queryParams);
}

function initMap(queryParams) {
	var mapDiv = document.getElementById("map");

	if (mapDiv) {
		var geoJson = JSON.parse(queryParams.coverage);
		var map = L.map(mapDiv);

		L.tileLayer('http://server.arcgisonline.com/arcgis/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}', {
			maxZoom: 18
		}).addTo(map);

		var fg = new L.FeatureGroup();
		var icon = getIcon(queryParams.theme);
		// var marker = getMarker(queryParams.theme);

		fg.addLayer(L.geoJson(geoJson, {
			pointToLayer: function (feature, latlng) {
				// return L.circleMarker(latlng, marker);
				return L.marker(latlng, {icon});
			},
			style: function(feature){
				switch(feature.geometry.type){
					case 'Line':
						return {color: "rgb(50,50,255)", weight: 2};
						break;

					case 'LineString':
						return {color: "rgb(50,50,255)", weight: 2};
						break;

					case 'MultiLineString':
						return {color: "rgb(50,50,255)", weight: 2};
						break;

					case 'Polygon':
						return {color: "rgb(255,50,50)", weight: 2};
						break;

					default:
						return {color: "rgb(50,255,50)", weight: 2};
				}
			}
		}));

		map.addLayer(fg);

		if (geoJson.type == "Point"){
			map.setView([geoJson.coordinates[1], geoJson.coordinates[0]], 3);
		} else {
			map.fitBounds(fg.getBounds());
		}
	}
}

function getIcon(theme){
	switch(theme){
		case 'NonICOS':
			return L.icon({
				iconUrl: 'https://static.icos-cp.eu/images/tmp/wdcgg.svg',
				iconSize:     [23, 28],
				iconAnchor:   [12, 28],
				popupAnchor:  [0, -23]
			});

		case 'Atmosphere':
			return L.icon({
				iconUrl: 'https://static.icos-cp.eu/share/stations/icons/as.png',
				iconSize:     [23, 28],
				iconAnchor:   [12, 28],
				popupAnchor:  [0, -23]
			});

		case 'Ecosystem':
			return L.icon({
				iconUrl: 'https://static.icos-cp.eu/share/stations/icons/es.png',
				iconSize:     [23, 28],
				iconAnchor:   [12, 28],
				popupAnchor:  [0, -23]
			});

		case 'Ocean':
			return L.icon({
				iconUrl: 'https://static.icos-cp.eu/share/stations/icons/os.png',
				iconSize:     [23, 28],
				iconAnchor:   [12, 28],
				popupAnchor:  [0, -23]
			});

		default:
			return L.icon({
				iconUrl: 'https://static.icos-cp.eu/constant/leaflet/0.7.7/css/images/marker-icon.png',
				shadowUrl: 'https://static.icos-cp.eu/constant/leaflet/0.7.7/css/images/marker-shadow.png',
				iconSize:     [25, 41],
				iconAnchor:   [13, 41]
			});
	}
}

function getMarker(theme){
	var marker = {
		radius: 5,
		weight: 2,
		color: 'black',
		fillColor: 'rgb(255,50,50)',
		fillOpacity: 1
	};

	switch(theme){
		case 'NonICOS':
			marker.fillColor = 'red';
			break;

		case 'atm':
			marker.fillColor = 'blue';
			break;

		case 'eco':
			marker.fillColor = 'green';
			break;

		case 'oce':
			marker.fillColor = 'blue';
			break;
	}

	return marker;
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

