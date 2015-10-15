$(function () {
	var stationsPromise = fetchStations();

	$("body").prepend('<div id="map" title="Station map"></div>');
	$( "#map" ).dialog({
		autoOpen: false,
		dialogClass: "no-close",
		resizable: false,
		height: "400",
		width: "600"
	});

	var config = {
		hiddenCols: [0, 1, 2, 3, 4, 5],
		latInd: 1,
		lonInd: 2,
		geoJson: 3,
		posDescInd: 4,
		themeShortInd: 5,
		themeInd: 6,
		shortNameInd: 8
	};

	stationsPromise
		.done(function(result){
			init(parseStationsJson(result), config);
		})
		.fail(function(request){
			console.log(request.responseText);
		});
});

function init(stations, config){
	$('#stationsTable').DataTable( {
		data: stations.rows,
		columns: stations.columns,
		columnDefs: [
			{
				//Hide some columns
				targets: config.hiddenCols,
				visible: false,
				searchable: false
			},
			{
				targets: [config.themeInd],
				fnCreatedCell: function (nTd, sData, oData, iRow, iCol) {
					if (oData[config.latInd] == "?" || oData[config.lonInd] == "?"){
						if (oData[config.geoJson] != "?"){

							var $icon = $('<span class="blue-lnk glyphicon glyphicon-globe"></span>');

							$icon.click(function(){
								showMap(this, oData[config.shortNameInd], oData[config.themeShortInd], null, null, JSON.parse(oData[config.geoJson]));
							});

							$(nTd).append("&nbsp;").append($icon);

						} else if(oData[config.posDescInd] != "?") {

							var $icon = $('<span class="lnk glyphicon glyphicon-globe"></span>');

							$icon.click(function(){
								showLocDesc(this, oData[config.shortNameInd], oData[config.posDescInd]);
							});

							$(nTd).append("&nbsp;").append($icon);

						}
					} else {

						var $icon = $('<span class="blue-lnk glyphicon glyphicon-globe"></span>');

						$icon.click(function(){
							showMap(this, oData[config.shortNameInd], oData[config.themeShortInd],
								parseFloat(oData[config.latInd]), parseFloat(oData[config.lonInd]), null);
						});

						$(nTd).append("&nbsp;").append($icon);

					}
				}
			}
		],
		//stateSave: true,
		lengthMenu: [[25, 50, 100, -1], [25, 50, 100, "All"]],
		//scrollX: true,

		initComplete: function () {

			this.api().columns().every( function (ind) {
				var column = this;

				var $headerControl = $('<div class="input-group">' +
					'<input class="suggestInput form-control" type="search" title="' + column.context[0].aoColumns[ind].title + '" />' +
					'<span class="input-group-btn">' +
					'<button type="button" class="btn btn-default">' +
					'<span class="glyphicon glyphicon-remove"></span>' +
					'</button></span></div>');
				var $suggestInput = $headerControl.find("input");
				var $suggestBtn = $headerControl.find("button");

				$headerControl.appendTo( $(column.header()).empty() );

				$suggestInput
					.on( 'click', function (event) {
						event.stopPropagation();

						if ($(this).val() == column.context[0].aoColumns[ind].title){
							$(this).val("");
							$(this).prop("title", "");
						}
					})
					.on( 'keyup', function (event) {
						var val = $.fn.dataTable.util.escapeRegex($(this).val());
						$(this).prop("title", val);
						column.search( val ? val : '', true, false).draw();
					})
					.on( 'blur', function (event) {
						if ($(this).val() == ""){
							$(this).val(column.context[0].aoColumns[ind].title);
							$(this).prop("title", column.context[0].aoColumns[ind].title);
						}
					})
					.autocomplete({
						source: extractSuggestions(column.data()),
						select: function(a, b){
							var val = b.item.value;
							$(this).prop("title", val);
							column.search( val ? val : '', true, false).draw();
						}
					});

				$suggestInput.val(column.context[0].aoColumns[ind].title);

				$suggestBtn
					.on( 'click', function (event, $suggestInput) {
						event.stopPropagation();

						var $input = $($(this).parent().siblings()[0]);

						$input.val("");
						column.search('').draw();
						$input.val(column.context[0].aoColumns[ind].title);
						$input.prop("title", "");
					})
					.prop("title", column.context[0].aoColumns[ind].title);

			});

		}
	});

}

function showMap(sender, title, theme, lat, lon, geoJson){
	var $dialog = $("#map").dialog();
	prepareDialog($dialog, sender, title);

	$("#map").find(".ol-viewport").show();

	if ($('#map').find('canvas').length == 0) {
		var mapQuestMap = new ol.layer.Tile({
			tag: "mapQuestMap",
			visible: true,
			source: new ol.source.MapQuest({layer: 'osm'})
		});

		var station = getVectorLayer({
			theme: theme,
			data: [{
				pos: [lon, lat],
				geoJson: geoJson
			}]
		});

		var view = new ol.View({
			center: ol.proj.transform([0, 0], 'EPSG:4326', 'EPSG:3857'),
			zoom: 5
		});

		if (isNumeric(lat) && isNumeric(lon)) {
			view = new ol.View({
				center: ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:3857'),
				zoom: 5
			});
		}

		var map = new ol.Map({
			layers: [mapQuestMap, station],
			target: 'map',
			view: view
		});

		if(geoJson != null){
			var extent = station.getSource().getExtent();
			view.fit(extent, map.getSize(), {
				padding: [10, 10, 10, 10]
			});
		}

		$('#map').data('map', map);

	} else {
		var map = $('#map').data('map');

		var stationLayer = map.getLayers().item(1);

		stationLayer.getSource().clear();
		stationLayer.getSource().addFeatures(getVectorFeatures({
				theme: theme,
				data: [{
					pos: [lon, lat],
					geoJson: geoJson
				}]
			})
		);

		if (geoJson == null) {
			map.getView().setCenter(ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:3857'));
			map.getView().setZoom(5);
		} else {
			var view = map.getView();
			var extent = stationLayer.getSource().getExtent();
			view.fit(extent, map.getSize(), {
				padding: [5, 5, 5, 5]
			});
		}
	}
}

function showLocDesc(sender, title, txt){
	var $dialog = $("#map").dialog();
	prepareDialog($dialog, sender, title);

	$("#map").find(".ol-viewport").hide();

	$dialog.prepend('<span id="locationDescSpan"><b>Position is not defined. Location description is:</b> ' + txt + '</span>');
}

function prepareDialog($dialog, sender, title){
	var $titlebar = $dialog.parents('.ui-dialog').find('.ui-dialog-titlebar');

	$titlebar.text("Station map - " + title);

	if($titlebar.find(".float-right").length == 0) {
		$('<button class="float-right btn btn-default btn-xs"><span class="glyphicon glyphicon-remove"></span></button>')
			.appendTo($titlebar).click(function () {
				$dialog.dialog("close");
			});
	}

	$dialog.dialog("open");
	$dialog.dialog("option", "position", { my: "left top", at: "right+50px top", of: sender});

	$("#map").find("#locationDescSpan").remove();
}

function extractSuggestions(data){
	var aSuggestion = [];
	data.unique().sort().each( function ( d, j ) {
		aSuggestion.push(d);
	});

	return aSuggestion;
}

function fetchStations(){
	var query = [
		'PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'SELECT',
		'(str(?s) AS ?id)',
		'(IF(bound(?lat), str(?lat), "?") AS ?latstr)',
		'(IF(bound(?lon), str(?lon), "?") AS ?lonstr)',
		'(IF(bound(?spatRef), str(?spatRef), "?") AS ?geoJson)',
		'(IF(bound(?locationDesc), str(?locationDesc), "?") AS ?location)',
		'(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?themeShort)',
		'(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?Theme)',
		'(str(?country) AS ?Country)',
		'(str(?sName) AS ?Short_name)',
		'(str(?lName) AS ?Long_name)',
		'(GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)',
		'(GROUP_CONCAT(?pIMail; separator=";") AS ?PI_mails)',
		'(str(?siteType) AS ?Site_type)',
		'(IF(bound(?elevationAboveSea), str(?elevationAboveSea), "?") AS ?Elevation_AS)',
		'(IF(bound(?elevationAboveGround), str(?elevationAboveGround), "?") AS ?Elevation_AG)',
		'(str(?stationClass) AS ?Station_class)',
		'(str(?stationKind) AS ?Station_kind)',
		'(str(?preIcosMeasurements) AS ?Pre_ICOS_meassurement)',
		'(IF(bound(?operationalDateEstimate), str(?operationalDateEstimate), "?") AS ?Operational_date_estimate)',
		'(str(?isOperational) AS ?Is_operational)',
		'(str(?fundingForConstruction) AS ?Funding_for_construction)',
		'FROM <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'WHERE {',
		'?s a ?class .',
		'OPTIONAL{?s cpst:hasLat ?lat } .',
		'OPTIONAL{?s cpst:hasLon ?lon } .',
		'OPTIONAL{?s cpst:hasSpatialReference ?spatRef } .',
		'OPTIONAL{?s cpst:hasLocationDescription ?locationDesc } .',
		'?s cpst:hasCountry ?country .',
		'?s cpst:hasShortName ?sName .',
		'?s cpst:hasLongName ?lName .',
		'?s cpst:hasPi ?pi .',
		'OPTIONAL{?pi cpst:hasFirstName ?piFname } .',
		'?pi cpst:hasLastName ?piLname .',
		'?pi cpst:hasEmail ?pIMail .',
		'?s cpst:hasSiteType ?siteType .',
		'OPTIONAL{?s cpst:hasElevationAboveSea ?elevationAboveSea } .',
		'OPTIONAL{?s cpst:hasElevationAboveGround ?elevationAboveGround } .',
		'?s cpst:hasStationClass ?stationClass .',
		'?s cpst:hasStationKind ?stationKind .',
		'?s cpst:hasPreIcosMeasurements ?preIcosMeasurements .',
		'OPTIONAL{?s cpst:hasOperationalDateEstimate ?operationalDateEstimate } .',
		'?s cpst:isAlreadyOperational ?isOperational .',
		'?s cpst:hasFundingForConstruction ?fundingForConstruction .',
		'}',
		'GROUP BY ?s ?lat ?lon ?spatRef ?locationDesc ?class ?country ?sName ?lName ?siteType ?elevationAboveSea',
		' ?elevationAboveGround ?stationClass ?stationKind ?preIcosMeasurements ?operationalDateEstimate ?isOperational ?fundingForConstruction'
	].join("\n");

	return $.ajax({
		type: "POST",
		data: {query: query},
		url: getSparqlUrl(),
		dataType: "json"
	});
}

function parseStationsJson(stationsJson){
	var themeName = {"AS": "Atmosphere", "ES": "Ecosystem", "OS": "Ocean"};
	var countries = {"AD":"Andorra","AE":"United Arab Emirates","AF":"Afghanistan","AG":"Antigua and Barbuda","AI":"Anguilla","AL":"Albania","AM":"Armenia","AO":"Angola","AQ":"Antarctica","AR":"Argentina","AS":"American Samoa","AT":"Austria","AU":"Australia","AW":"Aruba","AX":"Åland Islands","AZ":"Azerbaijan","BA":"Bosnia and Herzegovina","BB":"Barbados","BD":"Bangladesh","BE":"Belgium","BF":"Burkina Faso","BG":"Bulgaria","BH":"Bahrain","BI":"Burundi","BJ":"Benin","BL":"Saint Barthélemy","BM":"Bermuda","BN":"Brunei Darussalam","BO":"Bolivia, Plurinational State of","BQ":"Bonaire, Sint Eustatius and Saba","BR":"Brazil","BS":"Bahamas","BT":"Bhutan","BV":"Bouvet Island","BW":"Botswana","BY":"Belarus","BZ":"Belize","CA":"Canada","CC":"Cocos (Keeling) Islands","CD":"Congo, the Democratic Republic of the","CF":"Central African Republic","CG":"Congo","CH":"Switzerland","CI":"Côte d'Ivoire","CK":"Cook Islands","CL":"Chile","CM":"Cameroon","CN":"China","CO":"Colombia","CR":"Costa Rica","CU":"Cuba","CV":"Cabo Verde","CW":"Curaçao","CX":"Christmas Island","CY":"Cyprus","CZ":"Czech Republic","DE":"Germany","DJ":"Djibouti","DK":"Denmark","DM":"Dominica","DO":"Dominican Republic","DZ":"Algeria","EC":"Ecuador","EE":"Estonia","EG":"Egypt","EH":"Western Sahara","ER":"Eritrea","ES":"Spain","ET":"Ethiopia","FI":"Finland","FJ":"Fiji","FK":"Falkland Islands (Malvinas)","FM":"Micronesia, Federated States of","FO":"Faroe Islands","FR":"France","GA":"Gabon","GB":"United Kingdom of Great Britain and Northern Ireland","GD":"Grenada","GE":"Georgia","GF":"French Guiana","GG":"Guernsey","GH":"Ghana","GI":"Gibraltar","GL":"Greenland","GM":"Gambia","GN":"Guinea","GP":"Guadeloupe","GQ":"Equatorial Guinea","GR":"Greece","GS":"South Georgia and the South Sandwich Islands","GT":"Guatemala","GU":"Guam","GW":"Guinea-Bissau","GY":"Guyana","HK":"Hong Kong","HM":"Heard Island and McDonald Islands","HN":"Honduras","HR":"Croatia","HT":"Haiti","HU":"Hungary","ID":"Indonesia","IE":"Ireland","IL":"Israel","IM":"Isle of Man","IN":"India","IO":"British Indian Ocean Territory","IQ":"Iraq","IR":"Iran, Islamic Republic of","IS":"Iceland","IT":"Italy","JE":"Jersey","JM":"Jamaica","JO":"Jordan","JP":"Japan","KE":"Kenya","KG":"Kyrgyzstan","KH":"Cambodia","KI":"Kiribati","KM":"Comoros","KN":"Saint Kitts and Nevis","KP":"Korea, Democratic People's Republic of","KR":"Korea, Republic of","KW":"Kuwait","KY":"Cayman Islands","KZ":"Kazakhstan","LA":"Lao People's Democratic Republic","LB":"Lebanon","LC":"Saint Lucia","LI":"Liechtenstein","LK":"Sri Lanka","LR":"Liberia","LS":"Lesotho","LT":"Lithuania","LU":"Luxembourg","LV":"Latvia","LY":"Libya","MA":"Morocco","MC":"Monaco","MD":"Moldova, Republic of","ME":"Montenegro","MF":"Saint Martin (French part)","MG":"Madagascar","MH":"Marshall Islands","MK":"Macedonia, the former Yugoslav Republic of","ML":"Mali","MM":"Myanmar","MN":"Mongolia","MO":"Macao","MP":"Northern Mariana Islands","MQ":"Martinique","MR":"Mauritania","MS":"Montserrat","MT":"Malta","MU":"Mauritius","MV":"Maldives","MW":"Malawi","MX":"Mexico","MY":"Malaysia","MZ":"Mozambique","NA":"Namibia","NC":"New Caledonia","NE":"Niger","NF":"Norfolk Island","NG":"Nigeria","NI":"Nicaragua","NL":"Netherlands","NO":"Norway","NP":"Nepal","NR":"Nauru","NU":"Niue","NZ":"New Zealand","OM":"Oman","PA":"Panama","PE":"Peru","PF":"French Polynesia","PG":"Papua New Guinea","PH":"Philippines","PK":"Pakistan","PL":"Poland","PM":"Saint Pierre and Miquelon","PN":"Pitcairn","PR":"Puerto Rico","PS":"Palestine, State of","PT":"Portugal","PW":"Palau","PY":"Paraguay","QA":"Qatar","RE":"Réunion","RO":"Romania","RS":"Serbia","RU":"Russian Federation","RW":"Rwanda","SA":"Saudi Arabia","SB":"Solomon Islands","SC":"Seychelles","SD":"Sudan","SE":"Sweden","SG":"Singapore","SH":"Saint Helena, Ascension and Tristan da Cunha","SI":"Slovenia","SJ":"Svalbard and Jan Mayen","SK":"Slovakia","SL":"Sierra Leone","SM":"San Marino","SN":"Senegal","SO":"Somalia","SR":"Suriname","SS":"South Sudan","ST":"Sao Tome and Principe","SV":"El Salvador","SX":"Sint Maarten (Dutch part)","SY":"Syrian Arab Republic","SZ":"Swaziland","TC":"Turks and Caicos Islands","TD":"Chad","TF":"French Southern Territories","TG":"Togo","TH":"Thailand","TJ":"Tajikistan","TK":"Tokelau","TL":"Timor-Leste","TM":"Turkmenistan","TN":"Tunisia","TO":"Tonga","TR":"Turkey","TT":"Trinidad and Tobago","TV":"Tuvalu","TW":"Taiwan, Province of China","TZ":"Tanzania, United Republic of","UA":"Ukraine","UG":"Uganda","UM":"United States Minor Outlying Islands","US":"United States of America","UY":"Uruguay","UZ":"Uzbekistan","VA":"Holy See","VC":"Saint Vincent and the Grenadines","VE":"Venezuela, Bolivarian Republic of","VG":"Virgin Islands, British","VI":"Virgin Islands, U.S.","VN":"Viet Nam","VU":"Vanuatu","WF":"Wallis and Futuna","WS":"Samoa","YE":"Yemen","YT":"Mayotte","ZA":"South Africa","ZM":"Zambia","ZW":"Zimbabwe"};

	var columns = stationsJson.head.vars.map(function (currVal){
		var cols = {};
		cols.title = currVal;
		return cols;
	});

	var rows = stationsJson.results.bindings.map(function (currObj){
		var row = [];

		columns.forEach(function(colObj){
			if (colObj.title == "Theme"){
				row.push(themeName[currObj[colObj.title].value]);
			} else if (colObj.title == "Country"){
				row.push(countries[currObj[colObj.title].value] + " (" + currObj[colObj.title].value + ")");
			} else if (colObj.title == "PI_names" || colObj.title == "PI_mails"){
				row.push(currObj[colObj.title].value.replace(";", "<br>"));
			} else {
				row.push(currObj[colObj.title].value);
			}
		});

		return row;
	});

	columns.forEach(function(colObj){
		colObj.title = colObj.title.replace(/_/g, " ");
	});

	var stations = {};
	stations.rows = rows;
	stations.columns = columns;

	return stations;
}