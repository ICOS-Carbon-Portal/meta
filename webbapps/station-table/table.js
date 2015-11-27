$(function () {
	var config = {
		sparqlUrl: "https://meta.icos-cp.eu/sparql",
		hiddenCols: [0, 1, 2, 3, 4, 5],
		latInd: 1,
		lonInd: 2,
		geoJson: 3,
		posDescInd: 4,
		themeShortInd: 5,
		themeInd: 6,
		shortNameInd: 8,
		maxSegmentLengthDeg: 1
	};

	var configPromise = queryConfig();

	configPromise
		.done(function (result) {
			if (result.localSparqlUrl === undefined) {
				querySparql(config);
			} else {
				config.sparqlUrl = result.localSparqlUrl;
				querySparql(config);
			}
		})
		.fail(function (request) {
			querySparql(config);
		});
});

function querySparql(config){
	var stationsPromise = fetchStations(config.sparqlUrl);

	stationsPromise
		.done(function(result){
			init(parseStationsJson(result), config);
		})
		.fail(function(request){
			console.log(request.responseText);
		});
}

function init(stations, config){
	$("body").prepend('<div id="map" title="Station map"></div>');
	$( "#map" ).dialog({
		autoOpen: false,
		dialogClass: "no-close",
		resizable: false,
		height: "400",
		width: "600"
	});

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
								showMap(this, oData[config.shortNameInd], oData[config.themeShortInd],
									null, null, oData[config.geoJson], config);
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
								parseFloat(oData[config.latInd]), parseFloat(oData[config.lonInd]), null, config);
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

function showMap(sender, title, theme, lat, lon, geoJson, config){
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
		}, config);

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
			}, config)
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

function fetchStations(sparqlUrl){
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
		'(IF(bound(?country), str(?country), "?") AS ?Country)',
		'(str(?sName) AS ?Short_name)',
		'(str(?lName) AS ?Long_name)',
		'(GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)',
		'(IF(bound(?siteType), str(?siteType), "?") AS ?Site_type)',
		'(IF(bound(?elevationAboveSea), str(?elevationAboveSea), "?") AS ?Elevation_AS)',
		'(IF(bound(?elevationAboveGround), str(?elevationAboveGround), "?") AS ?Elevation_AG)',
		'(IF(bound(?stationClass), str(?stationClass), "?") AS ?Station_class)',
		'(IF(bound(?stationKind), str(?stationKind), "?") AS ?Station_kind)',
		'(IF(bound(?preIcosMeasurements), str(?preIcosMeasurements), "?") AS ?Pre_ICOS_meassurement)',
		'(IF(bound(?operationalDateEstimate), str(?operationalDateEstimate), "?") AS ?Operational_date_estimate)',
		'(IF(bound(?isOperational), str(?isOperational), "?") AS ?Is_operational)',
		'(IF(bound(?fundingForConstruction), str(?fundingForConstruction), "?") AS ?Funding_for_construction)',
		'FROM <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'WHERE {',
		'?s a ?class .',
		'OPTIONAL{?s cpst:hasLat ?lat . ?s cpst:hasLon ?lon } .',
		'OPTIONAL{?s cpst:hasSpatialReference ?spatRef } .',
		'OPTIONAL{?s cpst:hasLocationDescription ?locationDesc } .',
		'OPTIONAL{?s cpst:hasCountry ?country } .',
		'?s cpst:hasShortName ?sName .',
		'?s cpst:hasLongName ?lName .',
		'?s cpst:hasPi ?pi .',
		'OPTIONAL{?pi cpst:hasFirstName ?piFname } .',
		'?pi cpst:hasLastName ?piLname .',
		'OPTIONAL{?s cpst:hasSiteType ?siteType } .',
		'OPTIONAL{?s cpst:hasElevationAboveSea ?elevationAboveSea } .',
		'OPTIONAL{?s cpst:hasElevationAboveGround ?elevationAboveGround } .',
		'OPTIONAL{?s cpst:hasStationClass ?stationClass } .',
		'OPTIONAL{?s cpst:hasStationKind ?stationKind } .',
		'OPTIONAL{?s cpst:hasPreIcosMeasurements ?preIcosMeasurements } .',
		'OPTIONAL{?s cpst:hasOperationalDateEstimate ?operationalDateEstimate } .',
		'OPTIONAL{?s cpst:isAlreadyOperational ?isOperational } .',
		'OPTIONAL{?s cpst:hasFundingForConstruction ?fundingForConstruction } .',
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
	var themeName = {"AS": "Atmosphere", "ES": "Ecosystem", "OS": "Ocean"};

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