var queryParams = processQuery(window.location.search);
var isSites = window.location.host.endsWith("fieldsites.se");

if (queryParams.coverage || queryParams.station) {
	getGeoJson(queryParams).then(function(geoJsonArray){
		initMap(geoJsonArray);
	});
}

function initMap(locations) {
	var mapDiv = document.getElementById("map");

	if (mapDiv) {
		var map = L.map(mapDiv, {
			minZoom: 1,
			maxBounds: [[-90, -180],[90, 180]],
			scrollWheelZoom: window.top === window.self
		});

		var baseMaps = getBaseMaps(18);
		map.addLayer(baseMaps.Topographic);
		L.control.layers(baseMaps).addTo(map);

		locations.map(function({label, geoJson}){
			var fg = new L.FeatureGroup();
			var icon = getIcon(queryParams.icon);

			fg.addLayer(L.geoJson(geoJson, {
				pointToLayer: function (_feature, latlng) {
					return icon ? L.marker(latlng, {icon}) : L.marker(latlng);
				},
				style: function (feature) {
					//TODO Look into Feature/Geometry handling in L.geoJson
					var supported = ['Line', 'LineString', 'MultiLineString', 'Polygon', 'GeometryCollection', 'Feature', 'FeatureCollection'];
					if(supported.includes(feature.geometry.type))
						return {color: "rgb(50,50,255)", weight: 2};
					else
						return {color: "rgb(50,255,50)", weight: 2};
				}
			}));

			if (label) {
				fg.bindPopup(label);
			}

			map.addLayer(fg);

			if (geoJson == locations[0].geoJson) {
				if (locations[0].geoJson.type === "Point") {
					const zoom = isSites ? 12 : 4;
					map.setView([geoJson.coordinates[1], geoJson.coordinates[0]], zoom);
				} else {
					map.fitBounds(fg.getBounds());
				}
			}
		});
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
		&& L.icon({
			iconUrl,
			iconSize:     [23, 28],
			iconAnchor:   [12, 28],
			popupAnchor:  [0, -23]
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

function getGeoJson(queryParams) {
	return queryParams.station ?
		getStationLocations(queryParams.station) :
		Promise.resolve([{"geoJson": JSON.parse(decodeURIComponent(queryParams.coverage))}])
}

function getStationLocations(stationUrl) {
	return getJson(stationUrl)
	.then(function(result){
		try{
			const ownCoverage = [{
				"label": `<b>${result.org.name}</b>`,
				"geoJson": result.coverage.geo
			}];
			const sitesCoverage = result.specificInfo._type === 'sites'
				? result.specificInfo.sites.map(site => {
					return {
						"label": `<b>${site.self.label}</b><br>${site.ecosystem.label}`,
						"geoJson":site.location.geometry.geo
					}
				})
				: [];
			return ownCoverage.concat(sitesCoverage);
		} catch (err){
			return Promise.reject(err);
		}
	});
}

function getJson(url){
	return new Promise(function(resolve, reject){
		var req = new XMLHttpRequest();
		req.open('GET', `${url.replace(/^http:/, 'https:')}?format=json`);
		req.responseType = 'json';
		req.setRequestHeader('Accept', 'application/json');
		req.onreadystatechange = function(){
			try {
				if (req.readyState === 4) {
					if (req.status < 400 && req.status >= 200) {

						if (req.responseType === 'json')
							resolve(req.response);
							else resolve(JSON.parse(req.responseText || null));

					} else reject(req);
				}
			} catch (e) {
				 reject(e);
			}
		};

		req.send();
	});
}