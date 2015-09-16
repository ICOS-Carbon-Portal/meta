$(function () {
	var stationsPromise = fetchStations();

	stationsPromise
		.done(function(result){
			init(parseStationsJson(result));
		})
		.fail(function(request){
			console.log(request);
		});
});

function init(stations){
	var availableTags = ["ActionScript","AppleScript","Asp","BASIC","C","C++","Clojure","COBOL","ColdFusion","Erlang","Fortran","Groovy","Haskell","Java","JavaScript","Lisp","Perl","PHP","Python","Ruby","Scala","Scheme"	];

	$('#stationsTable').DataTable( {
		data: stations.rows,
		columns: stations.columns,
		columnDefs: [
			{
				//Hide the id column
				targets: [0],
				visible: false,
				searchable: false
			}
		],
		//stateSave: true,
		lengthMenu: [[25, 50, 100, -1], [25, 50, 100, "All"]],
		//scrollX: true,

		initComplete: function () {

			//Text box
			this.api().columns().every( function (ind) {
				var column = this;

				$('<input type="search" class="suggestInput" />')
					.appendTo( $(column.header()).empty() )
					.on( 'click', function (event) {
						event.stopPropagation();

						if ($(this).val() == column.context[0].aoColumns[ind].title){
							$(this).val("");
						} else {
							column.search('').draw();
							$(this).val("");
						}
					})
					.on( 'keyup', function (event) {
						var val = $.fn.dataTable.util.escapeRegex($(this).val());
						console.log("keyup search: " + val);
						column.search( val ? val : '', true, false).draw();
					})
					.on( 'blur', function (event) {
						event.stopPropagation();

						if ($(this).val() == ""){
							$(this).val(column.context[0].aoColumns[ind].title);
						}
					})
					.autocomplete({
						source: extractSuggestions(column.data())

						//select: function(event){
						//	var val = $.fn.dataTable.util.escapeRegex(
						//		$(this).val()
						//	);
						//	console.log("autocomplete search: " + val);
						//	//column.search( val ? val : '', true, false).draw();
						//}
					})
					.val(column.context[0].aoColumns[ind].title);

				//var $headerControl = $('<div class="input-group"><input class="suggestInput form-control" type="search" /><span class="input-group-addon">x</span></div>');
				//var $suggestInput = $headerControl.find("input");
				//var $suggestBtn = $headerControl.find("span");
				//
				//$headerControl.appendTo( $(column.header()).empty() );
				//
				//$suggestInput
				//	.on( 'click', function (event) {
				//		event.stopPropagation();
				//
				//		if ($(this).val() == column.context[0].aoColumns[ind].title){
				//			$(this).val("");
				//		} else {
				//			column.search('').draw();
				//			$(this).val("");
				//		}
				//	})
				//	.on( 'keyup', function (event) {
				//		var val = $.fn.dataTable.util.escapeRegex($(this).val());
				//		console.log("keyup search: " + val);
				//		column.search( val ? val : '', true, false).draw();
				//	})
				//	.on( 'blur', function (event) {
				//		event.stopPropagation();
				//
				//		if ($(this).val() == ""){
				//			$(this).val(column.context[0].aoColumns[ind].title);
				//		}
				//	})
				//	.autocomplete({
				//		source: extractSuggestions(column.data())
				//
				//		//select: function(event){
				//		//	var val = $.fn.dataTable.util.escapeRegex(
				//		//		$(this).val()
				//		//	);
				//		//	console.log("autocomplete search: " + val);
				//		//	//column.search( val ? val : '', true, false).draw();
				//		//}
				//	});
				//
				//$suggestInput.val(column.context[0].aoColumns[ind].title);
				//
				//$suggestBtn
				//	.on( 'click', function (event, $suggestInput) {
				//		event.stopPropagation();
				//
				//		$($(this).siblings()[0]).val("");
				//		column.search('').draw();
				//	});

			});
			//Drop down
			//this.api().columns().every( function (ind) {
			//	var column = this;
			//
			//	var select = $('<select><option value="">' + column.context[0].aoColumns[ind].title + '</option></select>')
			//		.appendTo( $(column.header()).empty() )
			//		.on( 'click', function (event) {
			//			event.stopPropagation();
			//			var val = $.fn.dataTable.util.escapeRegex(
			//				$(this).val()
			//			);
			//
			//			column
			//				.search( val ? '^'+val+'$' : '', true, false )
			//				.draw();
			//		} );
			//
			//	column.data().unique().sort().each( function ( d, j ) {
			//		select.append( '<option value="'+d+'">'+d+'</option>' )
			//	});
			//
			//});

		}
	});

}

function extractSuggestions(data){
	var aSuggestion = [];
	data.unique().sort().each( function ( d, j ) {
		aSuggestion.push(d);
	});

	return aSuggestion;
}

function fetchStations(){
	//Assumes first column is id
	var query = [
		'PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'SELECT',
		'(str(?s) AS ?id)',
		'(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?Theme)',
		'(str(?country) AS ?Country)',
		'(str(?sName) AS ?Short_name)',
		'(str(?lName) AS ?Long_name)',
		'(str(?pIName) AS ?PI_name)',
		'FROM <http://meta.icos-cp.eu/ontologies/stationentry/>',
		'WHERE {',
		'?s a ?class .',
		'?s cpst:hasCountry ?country .',
		'?s cpst:hasShortName ?sName .',
		'?s cpst:hasLongName ?lName .',
		'?s cpst:hasPiName ?pIName .',
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
	var stations = {};
	var rows = [];
	var columns = [];
	var themeName = {"AS": "Atmosphere", "ES": "Ecosystem", "OS": "OCEAN"};
	var countries = {"AD":"Andorra","AE":"United Arab Emirates","AF":"Afghanistan","AG":"Antigua and Barbuda","AI":"Anguilla","AL":"Albania","AM":"Armenia","AO":"Angola","AQ":"Antarctica","AR":"Argentina","AS":"American Samoa","AT":"Austria","AU":"Australia","AW":"Aruba","AX":"Åland Islands","AZ":"Azerbaijan","BA":"Bosnia and Herzegovina","BB":"Barbados","BD":"Bangladesh","BE":"Belgium","BF":"Burkina Faso","BG":"Bulgaria","BH":"Bahrain","BI":"Burundi","BJ":"Benin","BL":"Saint Barthélemy","BM":"Bermuda","BN":"Brunei Darussalam","BO":"Bolivia, Plurinational State of","BQ":"Bonaire, Sint Eustatius and Saba","BR":"Brazil","BS":"Bahamas","BT":"Bhutan","BV":"Bouvet Island","BW":"Botswana","BY":"Belarus","BZ":"Belize","CA":"Canada","CC":"Cocos (Keeling) Islands","CD":"Congo, the Democratic Republic of the","CF":"Central African Republic","CG":"Congo","CH":"Switzerland","CI":"Côte d'Ivoire","CK":"Cook Islands","CL":"Chile","CM":"Cameroon","CN":"China","CO":"Colombia","CR":"Costa Rica","CU":"Cuba","CV":"Cabo Verde","CW":"Curaçao","CX":"Christmas Island","CY":"Cyprus","CZ":"Czech Republic","DE":"Germany","DJ":"Djibouti","DK":"Denmark","DM":"Dominica","DO":"Dominican Republic","DZ":"Algeria","EC":"Ecuador","EE":"Estonia","EG":"Egypt","EH":"Western Sahara","ER":"Eritrea","ES":"Spain","ET":"Ethiopia","FI":"Finland","FJ":"Fiji","FK":"Falkland Islands (Malvinas)","FM":"Micronesia, Federated States of","FO":"Faroe Islands","FR":"France","GA":"Gabon","GB":"United Kingdom of Great Britain and Northern Ireland","GD":"Grenada","GE":"Georgia","GF":"French Guiana","GG":"Guernsey","GH":"Ghana","GI":"Gibraltar","GL":"Greenland","GM":"Gambia","GN":"Guinea","GP":"Guadeloupe","GQ":"Equatorial Guinea","GR":"Greece","GS":"South Georgia and the South Sandwich Islands","GT":"Guatemala","GU":"Guam","GW":"Guinea-Bissau","GY":"Guyana","HK":"Hong Kong","HM":"Heard Island and McDonald Islands","HN":"Honduras","HR":"Croatia","HT":"Haiti","HU":"Hungary","ID":"Indonesia","IE":"Ireland","IL":"Israel","IM":"Isle of Man","IN":"India","IO":"British Indian Ocean Territory","IQ":"Iraq","IR":"Iran, Islamic Republic of","IS":"Iceland","IT":"Italy","JE":"Jersey","JM":"Jamaica","JO":"Jordan","JP":"Japan","KE":"Kenya","KG":"Kyrgyzstan","KH":"Cambodia","KI":"Kiribati","KM":"Comoros","KN":"Saint Kitts and Nevis","KP":"Korea, Democratic People's Republic of","KR":"Korea, Republic of","KW":"Kuwait","KY":"Cayman Islands","KZ":"Kazakhstan","LA":"Lao People's Democratic Republic","LB":"Lebanon","LC":"Saint Lucia","LI":"Liechtenstein","LK":"Sri Lanka","LR":"Liberia","LS":"Lesotho","LT":"Lithuania","LU":"Luxembourg","LV":"Latvia","LY":"Libya","MA":"Morocco","MC":"Monaco","MD":"Moldova, Republic of","ME":"Montenegro","MF":"Saint Martin (French part)","MG":"Madagascar","MH":"Marshall Islands","MK":"Macedonia, the former Yugoslav Republic of","ML":"Mali","MM":"Myanmar","MN":"Mongolia","MO":"Macao","MP":"Northern Mariana Islands","MQ":"Martinique","MR":"Mauritania","MS":"Montserrat","MT":"Malta","MU":"Mauritius","MV":"Maldives","MW":"Malawi","MX":"Mexico","MY":"Malaysia","MZ":"Mozambique","NA":"Namibia","NC":"New Caledonia","NE":"Niger","NF":"Norfolk Island","NG":"Nigeria","NI":"Nicaragua","NL":"Netherlands","NO":"Norway","NP":"Nepal","NR":"Nauru","NU":"Niue","NZ":"New Zealand","OM":"Oman","PA":"Panama","PE":"Peru","PF":"French Polynesia","PG":"Papua New Guinea","PH":"Philippines","PK":"Pakistan","PL":"Poland","PM":"Saint Pierre and Miquelon","PN":"Pitcairn","PR":"Puerto Rico","PS":"Palestine, State of","PT":"Portugal","PW":"Palau","PY":"Paraguay","QA":"Qatar","RE":"Réunion","RO":"Romania","RS":"Serbia","RU":"Russian Federation","RW":"Rwanda","SA":"Saudi Arabia","SB":"Solomon Islands","SC":"Seychelles","SD":"Sudan","SE":"Sweden","SG":"Singapore","SH":"Saint Helena, Ascension and Tristan da Cunha","SI":"Slovenia","SJ":"Svalbard and Jan Mayen","SK":"Slovakia","SL":"Sierra Leone","SM":"San Marino","SN":"Senegal","SO":"Somalia","SR":"Suriname","SS":"South Sudan","ST":"Sao Tome and Principe","SV":"El Salvador","SX":"Sint Maarten (Dutch part)","SY":"Syrian Arab Republic","SZ":"Swaziland","TC":"Turks and Caicos Islands","TD":"Chad","TF":"French Southern Territories","TG":"Togo","TH":"Thailand","TJ":"Tajikistan","TK":"Tokelau","TL":"Timor-Leste","TM":"Turkmenistan","TN":"Tunisia","TO":"Tonga","TR":"Turkey","TT":"Trinidad and Tobago","TV":"Tuvalu","TW":"Taiwan, Province of China","TZ":"Tanzania, United Republic of","UA":"Ukraine","UG":"Uganda","UM":"United States Minor Outlying Islands","US":"United States of America","UY":"Uruguay","UZ":"Uzbekistan","VA":"Holy See","VC":"Saint Vincent and the Grenadines","VE":"Venezuela, Bolivarian Republic of","VG":"Virgin Islands, British","VI":"Virgin Islands, U.S.","VN":"Viet Nam","VU":"Vanuatu","WF":"Wallis and Futuna","WS":"Samoa","YE":"Yemen","YT":"Mayotte","ZA":"South Africa","ZM":"Zambia","ZW":"Zimbabwe"};

	stationsJson.head.vars.forEach(function (colName){
		var col = {};
		col.title = colName;
		columns.push(col);
	});

	stationsJson.results.bindings.forEach(function(binding){
		var row = [];

		columns.forEach(function(colObj){
			if (colObj.title == "Theme"){
				row.push(themeName[binding[colObj.title].value]);
			} else if (colObj.title == "Country"){
				row.push(countries[binding[colObj.title].value] + " (" + binding[colObj.title].value + ")");
			} else {
				row.push(binding[colObj.title].value);
			}
		});

		rows.push(row);

	});

	columns.forEach(function(colObj){
		colObj.title = colObj.title.replace("_", " ");
	});

	stations.rows = rows;
	stations.columns = columns;

	return stations;
}