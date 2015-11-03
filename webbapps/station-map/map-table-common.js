function getVectorLayer(stations, config){
	var vectorSource = new ol.source.Vector({
		features: getVectorFeatures(stations, config)
	});

	return new ol.layer.Vector({
		source: vectorSource
	});
}

function getVectorFeatures(stations, config){
	var mapFeature;
	var features = [];

	var iconStyle = new ol.style.Style({
		image: getIcon(stations.theme)
	});

	var lineStyle = [
		new ol.style.Style({
			stroke: new ol.style.Stroke({
				color: 'lightskyblue',
				width: 4
			})
		}),
		new ol.style.Style({
			stroke: new ol.style.Stroke({
				color: 'darkblue',
				width: 2
			})
		})
	];

	stations.data.forEach(function (station, config){
		if (isNumeric(station.pos[0]) && isNumeric(station.pos[1])) {
			mapFeature = new ol.Feature({
				geometry: new ol.geom.Point(ol.proj.fromLonLat(station.pos))
			});

			mapFeature.setStyle(iconStyle);

		} else if (station.geoJson != null){
			var originalGeoJson = JSON.parse(station.geoJson);

			var start, stop;
			var newGeoJson = JSON.parse('{"type":"LineString","coordinates":[]}');

			for (var i=0; i<originalGeoJson.coordinates.length-1; i++){
				start = {lon: originalGeoJson.coordinates[i][0], lat: originalGeoJson.coordinates[i][1]};
				stop = {lon: originalGeoJson.coordinates[i+1][0], lat: originalGeoJson.coordinates[i+1][1]};

				newGeoJson.coordinates.push([start.lon, start.lat]);

				var gc = new GreatCircle(start, stop);
				var degreeDist = gc.degrDist();

				if (degreeDist > config.maxSegmentLengthDeg){
					//Add more segments
					var segments = Math.ceil(degreeDist / config.maxSegmentLengthDeg);
					var segmentStep = 1 / segments;

					for (var s=1; s<=segments; s++){
						var newCoord = gc.interpolate(s * segmentStep);
						newGeoJson.coordinates.push(newCoord);
					}
				} else {
					newGeoJson.coordinates.push([stop.lon, stop.lat]);
				}
			}

			var f = new ol.format.GeoJSON();

			var geom = f.readGeometry(newGeoJson, {
				dataProjection: "EPSG:4326",
				featureProjection: "EPSG:3857"
			});

			mapFeature = new ol.Feature(geom);
			mapFeature.setStyle(lineStyle);
		}

		features.push(mapFeature);

		//Add all other attributes
		for (var name in station) {
			if (name != "pos") {
				mapFeature.set(name, station[name]);
			}
		}
	});

	return features;
}

function getIcon(theme){
	var icon = {
		anchor: [11, 28],
		anchorXUnits: 'pixels',
		anchorYUnits: 'pixels',
		opacity: 1
	};

	switch (theme){
		case "AS":
			icon.src = 'icons/as.png';
			break;

		case "ES":
			icon.src = 'icons/es.png';
			break;

		case "OS":
			icon.src = 'icons/os.png';
			break;
	}

	return new ol.style.Icon(icon);
}

function queryConfig(){
	return $.ajax({
		type: "GET",
		url: "local-config.json",
		dataType: "json"
	});
}

function isNumeric(n) {
	return !isNaN(parseFloat(n)) && isFinite(n);
}

// Code lifted from https://github.com/springmeyer/arc.js/blob/gh-pages/arc.js and modified slightly
// Copyright (c) Dane Springmeyer
// License: https://github.com/springmeyer/arc.js/blob/gh-pages/LICENSE.md
var D2R = Math.PI / 180;
var R2D = 180 / Math.PI;

var Coord = function(lon,lat) {
	this.lon = lon;
	this.lat = lat;
	this.x = D2R * lon;
	this.y = D2R * lat;
};

Coord.prototype.view = function() {
	return String(this.lon).slice(0, 4) + ',' + String(this.lat).slice(0, 4);
};

var GreatCircle = function(start, end) {
	if (!start || start.lon === undefined || start.lat === undefined) {
		throw new Error("GreatCircle constructor expects two args: start and end objects with lon and lat properties");
	}
	if (!end || end.lon === undefined || end.lat === undefined) {
		throw new Error("GreatCircle constructor expects two args: start and end objects with lon and lat properties");
	}
	this.start = new Coord(start.lon,start.lat);
	this.end = new Coord(end.lon,end.lat);

	var w = this.start.x - this.end.x;
	var h = this.start.y - this.end.y;
	var z = Math.pow(Math.sin(h / 2.0), 2) +
		Math.cos(this.start.y) *
		Math.cos(this.end.y) *
		Math.pow(Math.sin(w / 2.0), 2);
	this.g = 2.0 * Math.asin(Math.sqrt(z));

	if (this.g == Math.PI) {
		throw new Error('it appears ' + start.view() + ' and ' + end.view() + " are 'antipodal', e.g diametrically opposite, thus there is no single route but rather infinite");
	} else if (isNaN(this.g)) {
		throw new Error('could not calculate great circle between ' + start + ' and ' + end);
	}
};

GreatCircle.prototype.degrDist = function(){
	return this.g * R2D;
};

GreatCircle.prototype.mDist = function(){
	return this.g * 6378137;
};

GreatCircle.prototype.interpolate = function(f) {
	var A = Math.sin((1 - f) * this.g) / Math.sin(this.g);
	var B = Math.sin(f * this.g) / Math.sin(this.g);
	var x = A * Math.cos(this.start.y) * Math.cos(this.start.x) + B * Math.cos(this.end.y) * Math.cos(this.end.x);
	var y = A * Math.cos(this.start.y) * Math.sin(this.start.x) + B * Math.cos(this.end.y) * Math.sin(this.end.x);
	var z = A * Math.sin(this.start.y) + B * Math.sin(this.end.y);
	var lat = R2D * Math.atan2(z, Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2)));
	var lon = R2D * Math.atan2(y, x);
	return [lon, lat];
};