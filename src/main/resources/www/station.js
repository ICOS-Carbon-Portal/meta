var queryParams = processQuery(window.location.search);
var isSites = window.location.host.endsWith("fieldsites.se");

if (queryParams.coverage){
	initMap(queryParams);
}

function initMap(queryParams) {
	var mapDiv = document.getElementById("map");

	if (mapDiv) {
		var geoJson = JSON.parse(queryParams.coverage);
		var map = L.map(mapDiv, {
			minZoom: 1,
			maxBounds: [[-90, -180],[90, 180]]
		});

		var baseMaps = getBaseMaps(18);
		map.addLayer(baseMaps.Topographic);
		L.control.layers(baseMaps).addTo(map);

		if (geoJson.type === "Polygon"){
			L.Mask = L.Polygon.extend({
				options: {
					weight: 2,
					color: 'red',
					fillColor: '#333',
					fillOpacity: 0.5,
					clickable: false,
					outerBounds: new L.LatLngBounds([-90, -360], [90, 360])
				},

				initialize: function (latLngs, options) {
					var outerBoundsLatLngs = [
						this.options.outerBounds.getSouthWest(),
						this.options.outerBounds.getNorthWest(),
						this.options.outerBounds.getNorthEast(),
						this.options.outerBounds.getSouthEast()
					];
					L.Polygon.prototype.initialize.call(this, [outerBoundsLatLngs, latLngs], options);
				},

			});

			L.mask = function (latLngs, options) {
				return new L.Mask(latLngs, options);
			};

			var mask = getMask(geoJson);
			L.mask(mask).addTo(map);
			map.fitBounds(mask);

		} else {
			var fg = new L.FeatureGroup();
			var icon = getIcon(queryParams.icon);

			fg.addLayer(L.geoJson(geoJson, {
				pointToLayer: function (feature, latlng) {
					// return L.circleMarker(latlng, marker);
					return L.marker(latlng, {icon});
				},
				style: function (feature) {
					switch (feature.geometry.type) {
						case 'Line':
							return {color: "rgb(50,50,255)", weight: 2};

						case 'LineString':
							return {color: "rgb(50,50,255)", weight: 2};

						case 'MultiLineString':
							return {color: "rgb(50,50,255)", weight: 2};

						default:
							return {color: "rgb(50,255,50)", weight: 2};
					}
				}
			}));

			map.addLayer(fg);

			if (geoJson.type === "Point") {
				const zoom = isSites ? 5 : 4;
				map.setView([geoJson.coordinates[1], geoJson.coordinates[0]], zoom);
			} else {
				map.fitBounds(fg.getBounds());
			}
		}
	}
}

function getMask(geoJson){
	var coordinates = geoJson.coordinates[0];
	var latLngs = [];

	coordinates.forEach(function(coord){
		latLngs.push(new L.LatLng(coord[1], coord[0]));
	});

	return latLngs;
}

function getIcon(iconUrl){
	return iconUrl
		? L.icon({
			iconUrl,
			iconSize:     [23, 28],
			iconAnchor:   [12, 28],
			popupAnchor:  [0, -23]
		})
		: L.icon({
			iconUrl:      'https://static.icos-cp.eu/constant/leaflet/0.7.7/css/images/marker-icon.png',
			shadowUrl:    'https://static.icos-cp.eu/constant/leaflet/0.7.7/css/images/marker-shadow.png',
			iconSize:     [25, 41],
			iconAnchor:   [13, 41]
		});
}

function getLmUrl(layer){
	return "//api.lantmateriet.se/open/topowebb-ccby/v1/wmts/token/a39d27b8-9fd6-3770-84cb-2589b772b8ca/1.0.0/"
		+ layer
		+ "/default/3857/{z}/{y}/{x}.png";
}

function getBaseMaps(maxZoom){
	var topoLM = L.tileLayer(window.location.protocol + getLmUrl('topowebb'), {
		maxZoom: 15
	});

	var topoTonedLM = L.tileLayer(window.location.protocol + getLmUrl('topowebb_nedtonad'), {
		maxZoom: 15
	});

	var topo = L.tileLayer(window.location.protocol + '//server.arcgisonline.com/arcgis/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}', {
		maxZoom
	});

	var image = L.tileLayer(window.location.protocol + '//server.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
		maxZoom
	});

	var osm = L.tileLayer(window.location.protocol + "//{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
		maxZoom
	});

	return isSites
	? {
		"Topographic": topoLM,
		"Topographic Toned": topoTonedLM,
		"Satellite": image,
		"OSM": osm
	}
	: {
		"Topographic": topo,
		"Satellite": image,
		"OSM": osm
	};
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

