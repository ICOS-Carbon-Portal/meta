function initMap() {
	//Layer 0
	var worldMap = new ol.layer.Tile({
		visible: true,
		//source: new ol.source.MapQuest({layer: 'osm'})
		//source: new ol.source.OSM()
		source: new ol.source.BingMaps({
			key: 'AnVI2I3JgdBbAH42y_nepiei9Gx_mxk0pL9gaqs59-thEV66RVxdZF45YtEtX98Y',
			imagerySet: 'Road'
		})
	});

	//Layer 1
	var worldAerial = new ol.layer.Tile({
		visible: false,
		source: new ol.source.BingMaps({
			key: 'AnVI2I3JgdBbAH42y_nepiei9Gx_mxk0pL9gaqs59-thEV66RVxdZF45YtEtX98Y',
			imagerySet: 'Aerial'
		})
	});

	//Layer 2
	var worldWaterColor = new ol.layer.Tile({
		visible: false,
		source: new ol.source.Stamen({
			layer: 'watercolor'
		})
	});

	var view = new ol.View({
		center: ol.proj.transform([10, 55], 'EPSG:4326', 'EPSG:3857'),
		zoom: 4
	});

	var map = new ol.Map({
		layers: [worldMap, worldAerial, worldWaterColor, addStations()],
		target: 'map',
		view: view,
		//renderer: 'canvas'
		renderer: 'webgl'
	});

	addSwitchBgMap(map);
}

function addSwitchBgMap(map){
	var $bgMaps = $("input[name=bgMaps]");
	$bgMaps.filter('[value=map]').prop('checked', true);

	if ($bgMaps.data("event") == null){

		$bgMaps.change(function(){
			var selectedValue = $bgMaps.filter(':checked').val();

			switch (selectedValue){
				case "map":
					map.getLayers().item(0).setVisible(true);
					map.getLayers().item(1).setVisible(false);
					map.getLayers().item(2).setVisible(false);
					break;

				case "sat":
					map.getLayers().item(0).setVisible(false);
					map.getLayers().item(1).setVisible(true);
					map.getLayers().item(2).setVisible(false);
					break;

				case "water":
					map.getLayers().item(0).setVisible(false);
					map.getLayers().item(1).setVisible(false);
					map.getLayers().item(2).setVisible(true);
					break;
			}
		});
	}
}

function addStations(){
	var atlasManager = new ol.style.AtlasManager({
		initialSize: 512
	});

	var symbolInfo = [{
		opacity: 1.0,
		scale: 1.0,
		fillColor: 'rgba(255, 153, 0, 0.4)',
		strokeColor: 'rgba(255, 204, 0, 0.2)'
	}, {
		opacity: 0.75,
		scale: 1.25,
		fillColor: 'rgba(70, 80, 224, 0.4)',
		strokeColor: 'rgba(12, 21, 138, 0.2)'
	}, {
		opacity: 0.5,
		scale: 1.5,
		fillColor: 'rgba(66, 150, 79, 0.4)',
		strokeColor: 'rgba(20, 99, 32, 0.2)'
	}, {
		opacity: 1.0,
		scale: 1.0,
		fillColor: 'rgba(176, 61, 35, 0.4)',
		strokeColor: 'rgba(145, 43, 20, 0.2)'
	}];

	var radiuses = [6];
	var symbolCount = symbolInfo.length * radiuses.length * 2;
	var symbols = [];
	var i, j;
	for (i = 0; i < symbolInfo.length; ++i) {
		var info = symbolInfo[i];
		for (j = 0; j < radiuses.length; ++j) {
			// circle symbol
			symbols.push(new ol.style.Circle({
				opacity: info.opacity,
				scale: info.scale,
				radius: radiuses[j],
				fill: new ol.style.Fill({
					color: info.fillColor
				}),
				stroke: new ol.style.Stroke({
					color: info.strokeColor,
					width: 1
				}),
				// by passing the atlas manager to the symbol,
				// the symbol will be added to an atlas
				atlasManager: atlasManager
			}));

			// star symbol
			symbols.push(new ol.style.RegularShape({
				points: 8,
				opacity: info.opacity,
				scale: info.scale,
				radius: radiuses[j],
				radius2: radiuses[j] * 0.7,
				angle: 1.4,
				fill: new ol.style.Fill({
					color: info.fillColor
				}),
				stroke: new ol.style.Stroke({
					color: info.strokeColor,
					width: 1
				}),
				atlasManager: atlasManager
			}));
		}
	}

	var featureCount = 1000;
	var features = new Array(featureCount);
	var feature, geometry;
	var e = 25000000;
	for (i = 0; i < featureCount; ++i) {
		geometry = new ol.geom.Point(
			[2 * e * Math.random() - e, 2 * e * Math.random() - e]);
		feature = new ol.Feature(geometry);
		feature.setStyle(
			new ol.style.Style({
				image: symbols[i % symbolCount]
			})
		);
		features[i] = feature;
	}

	var vectorSource = new ol.source.Vector({
		features: features
	});
	var vector = new ol.layer.Vector({
		source: vectorSource
	});

	return vector;
}