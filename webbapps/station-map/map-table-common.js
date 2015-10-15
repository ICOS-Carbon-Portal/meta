function getVectorLayer(stations){
	var vectorSource = new ol.source.Vector({
		features: getVectorFeatures(stations)
	});

	return new ol.layer.Vector({
		source: vectorSource
	});
}

function getVectorFeatures(stations){
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

	stations.data.forEach(function (station){
		if (isNumeric(station.pos[0]) && isNumeric(station.pos[1])) {
			mapFeature = new ol.Feature({
				geometry: new ol.geom.Point(ol.proj.fromLonLat(station.pos))
			});

			mapFeature.setStyle(iconStyle);

		} else if (station.geoJson != null){
			var f = new ol.format.GeoJSON();

			var geom = f.readGeometry(station.geoJson, {
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

function getSparqlUrl(){
	if (location.hostname == "static.icos-cp.eu") {
		return "https://meta.icos-cp.eu/sparql";
	} else {
		return "http://127.0.0.1:9094/sparql"
	}
}

function isNumeric(n) {
	return !isNaN(parseFloat(n)) && isFinite(n);
}