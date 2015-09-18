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
		'(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?theme)',
		'(str(?lat) AS ?latstr)',
		'(str(?lon) AS ?lonstr) ',
		'(concat(?piFname, " ", ?piLname) AS ?PI_name)',
		'(str(?sName) AS ?Short_name)',
		'(str(?lName) AS ?Long_name)',
		'(str(?country) AS ?Country)',
		//'FROM <http://meta.icos-cp.eu/ontologies/stationtest/>',
		'FROM <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'WHERE {',
		'?s a ?class .',
		'?s cpst:hasLat ?lat .',
		'?s cpst:hasLon ?lon .',
		'?s cpst:hasPi ?pi .',
		'?pi cpst:hasFirstName ?piFname .',
		'?pi cpst:hasLastName ?piLname .',
		'?s cpst:hasShortName ?sName .',
		'?s cpst:hasLongName ?lName .',
		'?s cpst:hasCountry ?country .',
		'}'
	].join("\n");

	query = encodeURIComponent(query);

	return $.ajax({
		type: "GET",
		url: "https://meta.icos-cp.eu/sparql?query=" + query,
		//url: "http://127.0.0.1:9094/sparql?query=" + query,
		dataType: "json"
	});
}

function parseStationsJson(stationsJson){
	var stationsAS = [];
	var stationsES = [];
	var stationsOS = [];
	var countries = {"AD":"Andorra","AE":"United Arab Emirates","AF":"Afghanistan","AG":"Antigua and Barbuda","AI":"Anguilla","AL":"Albania","AM":"Armenia","AO":"Angola","AQ":"Antarctica","AR":"Argentina","AS":"American Samoa","AT":"Austria","AU":"Australia","AW":"Aruba","AX":"Åland Islands","AZ":"Azerbaijan","BA":"Bosnia and Herzegovina","BB":"Barbados","BD":"Bangladesh","BE":"Belgium","BF":"Burkina Faso","BG":"Bulgaria","BH":"Bahrain","BI":"Burundi","BJ":"Benin","BL":"Saint Barthélemy","BM":"Bermuda","BN":"Brunei Darussalam","BO":"Bolivia, Plurinational State of","BQ":"Bonaire, Sint Eustatius and Saba","BR":"Brazil","BS":"Bahamas","BT":"Bhutan","BV":"Bouvet Island","BW":"Botswana","BY":"Belarus","BZ":"Belize","CA":"Canada","CC":"Cocos (Keeling) Islands","CD":"Congo, the Democratic Republic of the","CF":"Central African Republic","CG":"Congo","CH":"Switzerland","CI":"Côte d'Ivoire","CK":"Cook Islands","CL":"Chile","CM":"Cameroon","CN":"China","CO":"Colombia","CR":"Costa Rica","CU":"Cuba","CV":"Cabo Verde","CW":"Curaçao","CX":"Christmas Island","CY":"Cyprus","CZ":"Czech Republic","DE":"Germany","DJ":"Djibouti","DK":"Denmark","DM":"Dominica","DO":"Dominican Republic","DZ":"Algeria","EC":"Ecuador","EE":"Estonia","EG":"Egypt","EH":"Western Sahara","ER":"Eritrea","ES":"Spain","ET":"Ethiopia","FI":"Finland","FJ":"Fiji","FK":"Falkland Islands (Malvinas)","FM":"Micronesia, Federated States of","FO":"Faroe Islands","FR":"France","GA":"Gabon","GB":"United Kingdom of Great Britain and Northern Ireland","GD":"Grenada","GE":"Georgia","GF":"French Guiana","GG":"Guernsey","GH":"Ghana","GI":"Gibraltar","GL":"Greenland","GM":"Gambia","GN":"Guinea","GP":"Guadeloupe","GQ":"Equatorial Guinea","GR":"Greece","GS":"South Georgia and the South Sandwich Islands","GT":"Guatemala","GU":"Guam","GW":"Guinea-Bissau","GY":"Guyana","HK":"Hong Kong","HM":"Heard Island and McDonald Islands","HN":"Honduras","HR":"Croatia","HT":"Haiti","HU":"Hungary","ID":"Indonesia","IE":"Ireland","IL":"Israel","IM":"Isle of Man","IN":"India","IO":"British Indian Ocean Territory","IQ":"Iraq","IR":"Iran, Islamic Republic of","IS":"Iceland","IT":"Italy","JE":"Jersey","JM":"Jamaica","JO":"Jordan","JP":"Japan","KE":"Kenya","KG":"Kyrgyzstan","KH":"Cambodia","KI":"Kiribati","KM":"Comoros","KN":"Saint Kitts and Nevis","KP":"Korea, Democratic People's Republic of","KR":"Korea, Republic of","KW":"Kuwait","KY":"Cayman Islands","KZ":"Kazakhstan","LA":"Lao People's Democratic Republic","LB":"Lebanon","LC":"Saint Lucia","LI":"Liechtenstein","LK":"Sri Lanka","LR":"Liberia","LS":"Lesotho","LT":"Lithuania","LU":"Luxembourg","LV":"Latvia","LY":"Libya","MA":"Morocco","MC":"Monaco","MD":"Moldova, Republic of","ME":"Montenegro","MF":"Saint Martin (French part)","MG":"Madagascar","MH":"Marshall Islands","MK":"Macedonia, the former Yugoslav Republic of","ML":"Mali","MM":"Myanmar","MN":"Mongolia","MO":"Macao","MP":"Northern Mariana Islands","MQ":"Martinique","MR":"Mauritania","MS":"Montserrat","MT":"Malta","MU":"Mauritius","MV":"Maldives","MW":"Malawi","MX":"Mexico","MY":"Malaysia","MZ":"Mozambique","NA":"Namibia","NC":"New Caledonia","NE":"Niger","NF":"Norfolk Island","NG":"Nigeria","NI":"Nicaragua","NL":"Netherlands","NO":"Norway","NP":"Nepal","NR":"Nauru","NU":"Niue","NZ":"New Zealand","OM":"Oman","PA":"Panama","PE":"Peru","PF":"French Polynesia","PG":"Papua New Guinea","PH":"Philippines","PK":"Pakistan","PL":"Poland","PM":"Saint Pierre and Miquelon","PN":"Pitcairn","PR":"Puerto Rico","PS":"Palestine, State of","PT":"Portugal","PW":"Palau","PY":"Paraguay","QA":"Qatar","RE":"Réunion","RO":"Romania","RS":"Serbia","RU":"Russian Federation","RW":"Rwanda","SA":"Saudi Arabia","SB":"Solomon Islands","SC":"Seychelles","SD":"Sudan","SE":"Sweden","SG":"Singapore","SH":"Saint Helena, Ascension and Tristan da Cunha","SI":"Slovenia","SJ":"Svalbard and Jan Mayen","SK":"Slovakia","SL":"Sierra Leone","SM":"San Marino","SN":"Senegal","SO":"Somalia","SR":"Suriname","SS":"South Sudan","ST":"Sao Tome and Principe","SV":"El Salvador","SX":"Sint Maarten (Dutch part)","SY":"Syrian Arab Republic","SZ":"Swaziland","TC":"Turks and Caicos Islands","TD":"Chad","TF":"French Southern Territories","TG":"Togo","TH":"Thailand","TJ":"Tajikistan","TK":"Tokelau","TL":"Timor-Leste","TM":"Turkmenistan","TN":"Tunisia","TO":"Tonga","TR":"Turkey","TT":"Trinidad and Tobago","TV":"Tuvalu","TW":"Taiwan, Province of China","TZ":"Tanzania, United Republic of","UA":"Ukraine","UG":"Uganda","UM":"United States Minor Outlying Islands","US":"United States of America","UY":"Uruguay","UZ":"Uzbekistan","VA":"Holy See","VC":"Saint Vincent and the Grenadines","VE":"Venezuela, Bolivarian Republic of","VG":"Virgin Islands, British","VI":"Virgin Islands, U.S.","VN":"Viet Nam","VU":"Vanuatu","WF":"Wallis and Futuna","WS":"Samoa","YE":"Yemen","YT":"Mayotte","ZA":"South Africa","ZM":"Zambia","ZW":"Zimbabwe"};

	var columns = stationsJson.head.vars.map(function (currVal){
		return currVal;
	});

	stationsJson.results.bindings.forEach(function(binding){
		var tmp = {};
		var lat = null;
		var lon = null;
		var theme = null;

		columns.forEach(function (colName){
			switch(colName){
				case "theme":
					theme = binding[colName].value;
					break;

				case "latstr":
					lat = parseFloat(binding[colName].value);
					break;

				case "lonstr":
					lon = parseFloat(binding[colName].value);
					break;

				default:
					if (colName == "Country") {
						tmp[colName] = countries[binding[colName].value] + " (" + binding[colName].value + ")";
					} else {
						tmp[colName] = binding[colName].value;
					}
			}
		});

		tmp.pos = [lon, lat];

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