$(function () {
	var config = {
		sparqlUrl: "https://meta.icos-cp.eu/sparql",
		maxSegmentLengthDeg: 1
	};

	var configPromise = queryConfig();

	configPromise
		.done(function (result) {
			if (result.localSparqlUrl === undefined) {
				init(config);
			} else {
				config.sparqlUrl = result.localSparqlUrl;
				init(config);
			}
		})
		.fail(function (request) {
			init(config);
		});
});

function init(config){
	var stationsPromise = fetchStations(config.sparqlUrl);

	$('input:radio[name="bgMaps"][value="topoMapESRI"]').prop('checked', true);

	stationsPromise
		.done(function(result){
			initMap(parseStationsJson(result), config);
		})
		.fail(function(request){
			console.log(request);
		});
}

function initMap(stations, config) {
	//Layer 0
	var topoMapESRI = new ol.layer.Tile({
		tag: "topoMapESRI",
		visible: true,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/arcgis/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}'
		}),
		opacity: 0.8
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
	var worldWaterColorStamenLbl = new ol.layer.Tile({
		tag: "worldWaterColorStamen",
		visible: false,
		source: new ol.source.XYZ({
			url: 'http://server.arcgisonline.com/arcgis/rest/services/Reference/World_Boundaries_and_Places_Alternate/MapServer/tile/{z}/{y}/{x}'
		}),
		opacity: 0.6
	});

	//Layer 4
	var mapQuestMap = new ol.layer.Tile({
		tag: "mapQuestMap",
		visible: false,
		source: new ol.source.MapQuest({layer: 'osm'})
	});

	//Station layers
	var OsStations = getVectorLayer(stations.OS, config);
	OsStations.theme = "OS";
	
	var EsStations = getVectorLayer(stations.ES, config);
	EsStations.theme = "ES";
	
	var AsStations = getVectorLayer(stations.AS, config);
	AsStations.theme = "AS";
	

	var view = new ol.View({
		center: ol.proj.transform([10, 55], 'EPSG:4326', 'EPSG:3857'),
		zoom: 4
	});

	var map = new ol.Map({
		layers: [
			topoMapESRI,
			worldAerialBing,
			worldWaterColorStamen,
			worldWaterColorStamenLbl,
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

	//var pos = ol.proj.fromLonLat([17.479455, 60.086441]);
	//$("body").prepend('<div id="marker" style="width:5px;height:5px;border-radius:3px;border:1px solid red;" title="Marker"></div>');
	//
	//var marker = new ol.Overlay({
	//	position: pos,
	//	positioning: 'center-center',
	//	element: document.getElementById('marker'),
	//	stopEvent: true
	//});
	//map.addOverlay(marker);

	addSwitchBgMap(map);

	addBtnEv(AsStations);
	addBtnEv(EsStations);
	addBtnEv(OsStations);

	popup(map, stations.lookupTable);

	countStations(map);
	addRefreshEv(map, config);
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

function countStations(map){
	map.getLayers().forEach(function (layer){
		if(layer.theme !== undefined) {

			switch (layer.theme){
				case "AS":
					$("#stationCountAS").text(layer.getSource().getFeatures().length + " Atmosphere stations");
					break;

				case "ES":
					$("#stationCountES").text(layer.getSource().getFeatures().length + " Ecosystem stations");
					break;

				case "OS":
					$("#stationCountOS").text(layer.getSource().getFeatures().length + " Ocean stations");
					break;
			}
		}
	});
}

function addRefreshEv(map, config){
	$("#refreshBtn").click(function(){
		var stationsPromise = fetchStations(config.sparqlUrl);

		stationsPromise
			.done(function(result){
				var stations = parseStationsJson(result);

				var OsStations = getVectorLayer(stations.OS, config);
				OsStations.theme = "OS";

				var EsStations = getVectorLayer(stations.ES, config);
				EsStations.theme = "ES";

				var AsStations = getVectorLayer(stations.AS, config);
				AsStations.theme = "AS";

				map.getLayers().forEach(function (layer){
					if(layer.theme !== undefined) {

						layer.getSource().clear();

						switch (layer.theme){
							case "AS":
								layer.getSource().addFeatures(getVectorFeatures(stations.AS, config));
								break;

							case "ES":
								layer.getSource().addFeatures(getVectorFeatures(stations.ES, config));
								break;

							case "OS":
								layer.getSource().addFeatures(getVectorFeatures(stations.OS, config));
								break;
						}
					}
				});

				countStations(map);
				$("#refreshBtn").text("Refresh done");

				setTimeout(function(){
					$("#refreshBtn").text("Refresh stations");
				}, 3000);

			})
			.fail(function(request){
				console.log(request);
			});
	});
}

function popup(map, lookupTable){
	var $element = $("#popover");

	var popup = new ol.Overlay({
		element: $element[0],
		positioning: "bottom-center",
		stopEvent: false
	});
	map.addOverlay(popup);

	var lastFeature;

// display popup on click
	map.on("pointermove", function(evt) {
		var feature = map.forEachFeatureAtPixel(evt.pixel,
			function(feature, layer) {
				return feature;
			});

		if (feature) {
			var content = "";

			popup.setPosition(evt.coordinate);

			if($element.next('div.popover:visible').length == 0 || feature !== lastFeature) {
				$element.popover("destroy");
			}

			var stationAttributes = $.grep(lookupTable, function(l) {
				return l.id == feature.get("id")
			})[0].attr;

			for (var name in stationAttributes) {
				content += "<div><b>" + name.replace(/_/g, " ") + ":</b> " + stationAttributes[name] + "</div>";
			}

			$element.popover({
				placement: "top",
				html: true,
				title: "Station information",
				content: content
			});

			if($element.next('div.popover:visible').length == 0 || feature !== lastFeature) {
				$element.popover("show");
			}

			lastFeature = feature;

		} else if($element.next('div.popover:visible').length == 1) {
			$element.popover("destroy");
		}
	});
}

function addSwitchBgMap(map){
	var $bgMaps = $("input[name=bgMaps]");
	$bgMaps.filter('[value=map]').prop('checked', true);

	if ($bgMaps.data("event") == null){

		$bgMaps.change(function(){
			var selectedValue = $bgMaps.filter(':checked').val();

			map.getLayers().forEach(function(item, ind){
				var tag = item.get("tag");

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

function fetchStations(sparqlUrl){
	var query = [
		'PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'SELECT',
		'(str(?s) AS ?id)',
		'(IF(bound(?lat), str(?lat), "?") AS ?latstr)',
		'(IF(bound(?lon), str(?lon), "?") AS ?lonstr)',
		'(IF(bound(?spatRef), str(?spatRef), "?") AS ?geoJson)',
		'(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?themeShort)',
		'(IF(bound(?country), str(?country), "?") AS ?Country)',
		'(str(?sName) AS ?Short_name)',
		'(str(?lName) AS ?Long_name)',
		'(GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)',
		'(IF(bound(?siteType), str(?siteType), "?") AS ?Site_type)',
		'FROM <http://meta.icos-cp.eu/resources/stationentry/>',
		'WHERE {',
		'?s a ?class .',
		'OPTIONAL{?s cpst:hasLat ?lat . ?s cpst:hasLon ?lon } .',
		'OPTIONAL{?s cpst:hasSpatialReference ?spatRef } .',
		'OPTIONAL{?s cpst:hasCountry ?country } .',
		'?s cpst:hasShortName ?sName .',
		'?s cpst:hasLongName ?lName .',
		'?s cpst:hasPi ?pi .',
		'OPTIONAL{?pi cpst:hasFirstName ?piFname } .',
		'?pi cpst:hasLastName ?piLname .',
		'OPTIONAL{?s cpst:hasSiteType ?siteType } .',
		'}',
		'GROUP BY ?s ?lat ?lon ?spatRef ?locationDesc ?class ?country ?sName ?lName ?siteType ?elevationAboveSea',
		' ?elevationAboveGround ?stationClass ?stationKind ?preIcosMeasurements ?operationalDateEstimate ?isOperational ?fundingForConstruction'
	].join("\n");

	return $.ajax({
		type: "POST",
		data: {query: query},
		url: sparqlUrl,
		dataType: "json"
	});
}

function parseStationsJson(stationsJson){
	var stationsAS = [];
	var stationsES = [];
	var stationsOS = [];
	var lookupTable = []

	var columns = stationsJson.head.vars.map(function (currVal){
		return currVal;
	});

	stationsJson.results.bindings.forEach(function(binding){
		//Only include stations that have a lat-lon position or a spatial reference (geoJson)
		if((isNumeric(binding["latstr"].value) && isNumeric(binding["lonstr"].value)) || binding["geoJson"].value != "?") {
			var tmp = {};
			var lookupStation = {};
			lookupStation.attr = {};
			var lat = null;
			var lon = null;
			var geoJson = null;
			var theme = null;

			columns.forEach(function (colName) {
				switch (colName) {
					case "id":
						lookupStation.id = binding[colName].value;
						tmp[colName] = binding[colName].value;
						break;

					case "themeShort":
						theme = binding[colName].value;
						break;

					case "latstr":
						lat = parseFloat(binding[colName].value);
						break;

					case "lonstr":
						lon = parseFloat(binding[colName].value);
						break;

					case "geoJson":
						geoJson = binding[colName].value;
						break;

					default:
						if (colName == "Country") {
							tmp[colName] = countries[binding[colName].value] + " (" + binding[colName].value + ")";
						} else {
							tmp[colName] = binding[colName].value;
						}

						lookupStation.attr[colName] = tmp[colName];
				}
			});

			if (isNumeric(lon) && isNumeric(lat)) {
				tmp.pos = [lon, lat];
				tmp.geoJson = null;
			} else {
				tmp.pos = [];
				tmp.geoJson = geoJson;
			}

			switch (theme) {
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

			lookupTable.push(lookupStation);
		}
	});

	var stations = {};
	stations.AS = { theme: "AS", data: stationsAS };
	stations.ES = { theme: "ES", data: stationsES };
	stations.OS = { theme: "OS", data: stationsOS };
	stations.lookupTable = lookupTable;

	return stations;
}
