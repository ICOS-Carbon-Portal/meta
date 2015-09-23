function getPointLayer(stations){
	var vectorSource = new ol.source.Vector({
		features: getVectorFeatures(stations)
	});

	return new ol.layer.Vector({
		source: vectorSource
	});
}

function getIcon(theme){
	var icon = {
		anchor: [3, 20],
		anchorXUnits: 'pixels',
		anchorYUnits: 'pixels',
		opacity: 1
	};

	switch (theme){
		case "AS":
			icon.src = 'icons/as.svg';
			break;

		case "ES":
			icon.src = 'icons/es.svg';
			break;

		case "OS":
			icon.src = 'icons/os.svg';
			break;
	}

	return new ol.style.Icon(icon);
}

function getVectorFeatures(stations){
	var iconFeature;
	var features = [];

	var iconStyle = new ol.style.Style({
		image: getIcon(stations.theme)
	});

	stations.data.forEach(function (station){
		iconFeature = new ol.Feature({
			geometry: new ol.geom.Point(ol.proj.transform(station.pos, 'EPSG:4326', 'EPSG:3857'))
		});

		//Add all other attributes
		for (var name in station) {
			if (name != "pos") {
				iconFeature.set(name, station[name]);
			}
		}

		iconFeature.setStyle(iconStyle);
		features.push(iconFeature);
	});

	return features;
}