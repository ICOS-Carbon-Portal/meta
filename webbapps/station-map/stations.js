function init(){
	var stationsPromise = fetchStations();

	stationsPromise
		.done(function(result){
			initMap(parseStationsJson(result));
		})
		.fail(function(request){
			console.log(request);
		});
}

function initMap(stations) {
	//Layer 0
	var worlMapBing = new ol.layer.Tile({
		tag: "worlMapBing",
		visible: false,
		source: new ol.source.BingMaps({
			key: 'AnVI2I3JgdBbAH42y_nepiei9Gx_mxk0pL9gaqs59-thEV66RVxdZF45YtEtX98Y',
			imagerySet: 'Road'
		})
	});

	//Layer 1
	var worldAerialBing = new ol.layer.Tile({
		tag: "worldAerialBing",
		visible: false,
		source: new ol.source.BingMaps({
			key: 'AnVI2I3JgdBbAH42y_nepiei9Gx_mxk0pL9gaqs59-thEV66RVxdZF45YtEtX98Y',
			imagerySet: 'Aerial'
		})
	});

	//Layer 2
	var worldWaterColorStamen = new ol.layer.Tile({
		tag: "worldWaterColorStamen",
		visible: false,
		source: new ol.source.Stamen({
			layer: 'watercolor'
		})
	});

	//Layer 3
	var oceanESRI = new ol.layer.Tile({
		tag: "oceanESRI",
		visible: true,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/ArcGIS/rest/services/Ocean_Basemap/MapServer/tile/{z}/{y}/{x}'
		})
	});

	//Layer 4
	var physicalMapESRI = new ol.layer.Tile({
		tag: "physicalMapESRI",
		visible: false,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/ArcGIS/rest/services/World_Physical_Map/MapServer/tile/{z}/{y}/{x}'
		})
	});

	//Layer 5
	var shadedRelMapESRI = new ol.layer.Tile({
		tag: "shadedRelMapESRI",
		visible: false,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/ArcGIS/rest/services/World_Shaded_Relief/MapServer/tile/{z}/{y}/{x}'
		})
	});

	//Layer 6
	var grayMapESRI = new ol.layer.Tile({
		tag: "grayMapESRI",
		visible: false,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}'
		})
	});

	//Layer 7
	var bndryMapESRI = new ol.layer.Tile({
		tag: "physicalMapESRI",
		visible: false,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/arcgis/rest/services/Reference/World_Reference_Overlay/MapServer/tile/{z}/{y}/{x}'
		})
	});

	//Layer 8
	var mapQuestMap = new ol.layer.Tile({
		tag: "mapQuestMap",
		visible: false,
		source: new ol.source.MapQuest({layer: 'osm'})
	});

	//Station layers
	var OsStations = getPointLayer(stations.OS);
	OsStations.theme = "OS";
	
	var EsStations = getPointLayer(stations.ES);
	EsStations.theme = "ES";
	
	var AsStations = getPointLayer(stations.AS);
	AsStations.theme = "AS";
	

	var view = new ol.View({
		center: ol.proj.transform([10, 55], 'EPSG:4326', 'EPSG:3857'),
		zoom: 4
	});

	var map = new ol.Map({
		layers: [
			worlMapBing,
			worldAerialBing,
			worldWaterColorStamen,
			oceanESRI,
			physicalMapESRI,
			shadedRelMapESRI,
			grayMapESRI,
			bndryMapESRI,
			mapQuestMap,
			OsStations,
			EsStations,
			AsStations
		],
		target: 'map',
		view: view
		//renderer: 'canvas'
		//renderer: 'webgl'
	});

	addSwitchBgMap(map);

	addBtnEv(AsStations);
	addBtnEv(EsStations);
	addBtnEv(OsStations);

	popup(map);

	addRefreshEv(map);
}

function addBtnEv(layer){
	var $chBx = $("#" + layer.theme);

	$chBx.click(function(){
		if ($chBx.prop("checked") == true) {
			layer.setVisible(true);
		} else {
			layer.setVisible(false);
		}
	});

	if ($chBx.prop("checked") == false) {
		$chBx.click();
	}
}

function addRefreshEv(map){
	$("#refreshBtn").click(function(){
		var stationsPromise = fetchStations();

		stationsPromise
			.done(function(result){
				var stations = parseStationsJson(result);

				var OsStations = getPointLayer(stations.OS);
				OsStations.theme = "OS";

				var EsStations = getPointLayer(stations.ES);
				EsStations.theme = "ES";

				var AsStations = getPointLayer(stations.AS);
				AsStations.theme = "AS";

				map.getLayers().forEach(function (layer){
					if(layer.theme !== undefined) {

						layer.getSource().clear();

						switch (layer.theme){
							case "AS":
								layer.getSource().addFeatures(getVectorFeatures(stations.AS));
								break;

							case "ES":
								layer.getSource().addFeatures(getVectorFeatures(stations.ES));
								break;

							case "OS":
								layer.getSource().addFeatures(getVectorFeatures(stations.OS));
								break;
						}
					}
				});

			})
			.fail(function(request){
				console.log(request);
			});
	});
}

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

function popup(map){
	var $element = $("#popover");

	var popup = new ol.Overlay({
		element: $element[0],
		positioning: "bottom-center",
		stopEvent: false
	});
	map.addOverlay(popup);

// display popup on click
	map.on("pointermove", function(evt) {
		var feature = map.forEachFeatureAtPixel(evt.pixel,
			function(feature, layer) {
				return feature;
			});

		if (feature) {
			var content = "";

			for (var name in feature.q) {
				if (name != "geometry") {
					content += "<div><b>" + name.replace("_", " ") + ":</b> " + feature.get(name) + "</div>";
				}
			}

			popup.setPosition(evt.coordinate);
			$element.popover({
				placement: "top",
				html: true,
				title: "Station information",
				content: content
			});
			$element.popover("show");
		} else {
			$element.popover("destroy");
		}
	});

// change mouse cursor when over marker
	map.on("pointermove", function(e) {
		if (e.dragging) {
			$element.popover("destroy");
			return;
		}
		var pixel = map.getEventPixel(e.originalEvent);
		var hit = map.hasFeatureAtPixel(pixel);
		//map.getTarget().style.cursor = hit ? "pointer" : "";
	});
}

function addSwitchBgMap(map){
	var $bgMaps = $("input[name=bgMaps]");
	$bgMaps.filter('[value=map]').prop('checked', true);

	if ($bgMaps.data("event") == null){

		$bgMaps.change(function(){
			var selectedValue = $bgMaps.filter(':checked').val();

			map.getLayers().forEach(function(item, ind){
				var tag = item.rc.q.tag;

				if (tag != undefined){
					if (selectedValue == tag){
						map.getLayers().item(ind).setVisible(true);
					} else {
						map.getLayers().item(ind).setVisible(false);
					}
				}
			});
		});
	}
}

function fetchStations(){
	var query = [
		'PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'SELECT',
		'(str(?sName) AS ?Short_name)',
		'(str(?lName) AS ?Long_name)',
		'(str(?country) AS ?Country)',
		'(str(?lat) AS ?latstr)',
		'(str(?lon) AS ?lonstr) ',
		'(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?theme)',
		//'FROM <http://meta.icos-cp.eu/ontologies/stationtest/>',
		'FROM <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'WHERE {',
		'?s a ?class .',
		'?s cpst:hasShortName ?sName .',
		'?s cpst:hasLongName ?lName .',
		'?s cpst:hasCountry ?country .',
		'?s cpst:hasLat ?lat .',
		'?s cpst:hasLon ?lon .',
		'}'
	].join("\n");

	query = encodeURIComponent(query);

	return $.ajax({
		type: "GET",
		url: "https://meta.icos-cp.eu/sparql?query=" + query,
		dataType: "json"
	});
}

function parseStationsJson(stationsJson){
	var stationsAS = [];
	var stationsES = [];
	var stationsOS = [];

	stationsJson.results.bindings.forEach(function(bind){
		var tmp = {};
		var lat = null;
		var lon = null;
		var theme = null;

		for (var name in bind) {
			if (bind.hasOwnProperty(name)) {

				switch(name){
					case "theme":
						theme = bind[name].value;
						break;

					case "latstr":
						lat = parseFloat(bind[name].value);
						break;

					case "lonstr":
						lon = parseFloat(bind[name].value);
						break;

					default:
						tmp[name] = bind[name].value;
				}
			}
		}

		tmp.pos = [lon, lat];
		//console.log(tmp);

		switch (theme){
			case "AS":
				stationsAS.push(tmp);
				break;

			case "ES":
				stationsES.push(tmp);
				break;

			case "OS":
				stationsOS.push(tmp);
				break;
		}
	});

	var stations = {};
	stations.AS = { theme: "AS", data: stationsAS };
	stations.ES = { theme: "ES", data: stationsES };
	stations.OS = { theme: "OS", data: stationsOS };

	return stations;
}