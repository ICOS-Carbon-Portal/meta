const envri = window.envri;

var queryParams = processQuery(window.location.search);

if (queryParams.coverage || queryParams.station || queryParams.dobj || queryParams.coll) {
	getGeoJson(queryParams).then(function (geoJsonArray) {
		initMap(geoJsonArray);
	});
}

function initMap(locations) {
	var mapDiv = document.getElementById("map");
	if (!mapDiv) return;

	var map = L.map(mapDiv, {
		minZoom: 1,
		maxBounds: [[-90, -180],[90, 180]],
		scrollWheelZoom: window.top === window.self
	});

	var baseMaps = getBaseMaps(18);
	map.addLayer(baseMaps.Topographic);
	const icon = getIcon(queryParams.icon);
	var overlays = [];
	var layercontrol = L.control.layers(baseMaps, overlays).addTo(map);

	const featureGroups = locations.map(function ({ label, description, geoJson }) {
		const fg = new L.FeatureGroup();

		fg.addLayer(
			L.geoJson(geoJson, {
				pointToLayer: function (feature, latlng) {
					if(feature.properties && feature.properties.radius){ //this point is a circle
						L.circle(latlng, {
							radius: feature.properties.radius,
							color: "rgb(50,50,255)",
							weight: 1,
							fillOpacity: 0.4
						}).addTo(map);
						return null;
					} else if (feature.properties && feature.properties.pinkind) {
						return L.marker(latlng);
					} else {
						return icon ? L.marker(latlng, {icon}) : L.marker(latlng);
					}
				},
				onEachFeature: function(feature, layer) {
					if (envri == "SITES" && label) {
						layercontrol.addOverlay(layer, label);
						!!description
							? layer.bindPopup(label + ": " + description)
							: layer.bindPopup(label);
					} else if (feature.properties && feature.properties.label) {
						layercontrol.addOverlay(layer, feature.properties.label);
						layer.bindPopup(feature.properties.label);
					}
				},
				style: function (feature) {
					var supported = ['Line', 'LineString', 'MultiLineString', 'Polygon', 'GeometryCollection'];
					if(supported.includes(feature.geometry.type))
						return {color: "rgb(50,50,255)", weight: 2};
					else
						return {color: "rgb(50,255,50)", weight: 2};
				}
			})
		);

		if (!hasFeatureLabels(geoJson) && label) fg.bindPopup(label);

		map.addLayer(fg);
		return fg;
	});

	var allGeoms = locations.flatMap(loc => collectGeometries(loc.geoJson));

	var len = allGeoms.length;

	if(len === 1 && allGeoms[0].type === 'Point'){
		map.setView([allGeoms[0].coordinates[1], allGeoms[0].coordinates[0]], window.initialZoom);
	} else if(len > 0 ){
		const bounds = featureGroups.reduce(
			function(acc, curr){
				return acc.extend(curr.getBounds());
			},
			featureGroups[0].getBounds()
		);
		map.fitBounds(bounds, {maxZoom: 14 } );
	}

}

function hasFeatureLabels(geoJson){
	switch(geoJson.type){
		case 'FeatureCollection':
			return geoJson.features.some(hasFeatureLabels);
		case 'Feature':
			return geoJson.properties && !!(geoJson.properties.label);
		default:
			return false;
	}
}

function collectGeometries(geoJson){
	switch(geoJson.type){
		case 'FeatureCollection':
			return geoJson.features.flatMap(collectGeometries);
		case 'Feature':
			return collectGeometries(geoJson.geometry);
		case 'GeometryCollection':
			return geoJson.geometries.flatMap(collectGeometries);
		default:
			return geoJson;
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

function getLmUrl(layer) {
	var baseUrl = envri === "SITES" ? "fieldsites.se" : "icos-cp.eu";
	return `//maps.${baseUrl}/lm/open/topowebb-ccby/v1/wmts/1.0.0/`
		+ layer
		+ "/default/3857/{z}/{y}/{x}.png";
}

function getBaseMaps(maxZoom){
	var topoLM = L.tileLayer(window.location.protocol + getLmUrl('topowebb'), {
		maxNativeZoom: 14
	});

	var topoTonedLM = L.tileLayer(window.location.protocol + getLmUrl('topowebb_nedtonad'), {
		maxNativeZoom:14
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
	return envri == "SITES"
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
	return queryParams.station
		? getStationLocations(queryParams.station)
		: queryParams.dobj
			? getItemLocations(queryParams.dobj)
			: queryParams.coll
				? getItemLocations(queryParams.coll)
				: Promise.resolve([{"geoJson": JSON.parse(decodeURIComponent(queryParams.coverage))}]);
}

function getItemLocations(itemUrl){
	return getJson(itemUrl).then(res => {
		return [{geoJson: res.coverage ? res.coverage.geo : res.coverageGeo}];
	});
}

function getStationLocations(stationUrl) {
	return getJson(stationUrl)
	.then(function(result){
		try{
			const ownCoverage = [];
			if(result.location) ownCoverage.push({
				"label": `<b>${result.org.name}</b>`,
				"geoJson": {
					"type": "Point",
					"coordinates": [result.location.lon, result.location.lat]
				}
			});
			if(result.coverage) ownCoverage.push({
				"label": `<b>${result.coverage.label}</b>`,
				"geoJson": result.coverage.geo
			})

			const sitesCoverage = result.specificInfo._type === 'sites'
				? result.specificInfo.sites.map(site => {
					return {
						"label": `<b>${site.location.label}</b>`,
						"description": `<br>${site.ecosystem.label}`,
						"geoJson": site.location.geo
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