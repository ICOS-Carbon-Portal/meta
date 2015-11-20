function availableRequests(){
	var queries = [{
		name:"Get all properties",
		query:
`SELECT DISTINCT ?property
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
WHERE {
  ?s ?property ?o .
}`
		},
		{
			name:"Get stations",
			query:
`PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
SELECT
(str(?s) AS ?id)
(IF(bound(?lat), str(?lat), "?") AS ?latstr)
(IF(bound(?lon), str(?lon), "?") AS ?lonstr)
(IF(bound(?spatRef), str(?spatRef), "?") AS ?geoJson)
(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?themeShort)
(str(?country) AS ?Country)
(str(?sName) AS ?Short_name)
(str(?lName) AS ?Long_name)
(GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)
(str(?siteType) AS ?Site_type)
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
WHERE {
?s a ?class .
OPTIONAL{?s cpst:hasLat ?lat } .
OPTIONAL{?s cpst:hasLon ?lon } .
OPTIONAL{?s cpst:hasSpatialReference ?spatRef } .
?s cpst:hasCountry ?country .
?s cpst:hasShortName ?sName .
?s cpst:hasLongName ?lName .
?s cpst:hasPi ?pi .
OPTIONAL{?pi cpst:hasFirstName ?piFname } .
?pi cpst:hasLastName ?piLname .
?s cpst:hasSiteType ?siteType .
}
GROUP BY ?s ?lat ?lon ?spatRef ?locationDesc ?class ?country ?sName ?lName ?siteType`
		}];

	return queries;
}

function runQuery(){
	var start;
	var selectedReturnType = $("#returnTypeGrp > .active").text();
	var accept = "application/json; charset=utf-8";

	switch(selectedReturnType){
		case "JSON":
			accept = "application/json; charset=utf-8";
			break;

		case "CSV":
			accept = "text/csv; charset=utf-8";
			break;

		case "TSV":
			accept = "text/plain; charset=utf-8";
			break;
	}

	$.ajax({
		type: "POST",
		data: {query: $("#queryTA").val()},
		url: "/sparql",
		beforeSend: function(){
			start = new Date().getTime();
		},
		contentType: "application/x-www-form-urlencoded; charset=UTF-8",
		headers: { Accept: accept },
		success: function(result){
			var requestTime = new Date().getTime() - start;

			$("#result").show();
			$("#copyTxtBtn").show();

			postProcessing(result, selectedReturnType, requestTime);

			$("#saveBtn").click(function(){
				window.location = "/sparql?query=" + encodeURIComponent($("#queryTA").val());
			})

			$("#queryBtn").prop("class", "btn btn-primary");
			$("#messSpan").text("Query was successfull").show().delay(3000).fadeOut();
		},
		error: function(req, textStatus, errorThrown) {
			$("#result").show();
			$("#statusSpan").text("");
			$("#queryBtn").prop("class", "btn btn-danger");
			$("#messSpan").text("Could not execute query").show().delay(3000).fadeOut();
			console.log(req);
			$("#result").html(req.responseText);
		}
	});
}

function postProcessing(result, selectedReturnType, requestTime){
	var highlightTime = 0;

	if (selectedReturnType == "JSON") {
		var highlightStart = new Date().getTime();
		$("#result").html(syntaxHighlight(result));
		highlightTime = new Date().getTime() - highlightStart;

	} else if (selectedReturnType == "TSV") {
		$("#result").html(result.replace(/\</g,"&lt;"));

	} else {
		$("#result").html(result);
	}

	if (selectedReturnType == "JSON") {
		$("#statusSpan").text(
			result.results.bindings.length + " bindings returned (" +
			requestTime + " ms request, " + highlightTime + " ms styling).");
	} else {
		$("#statusSpan").text(
			result.split(/\n/).length - 2 + " rows returned (" + requestTime + " ms request).");
	}
}

function syntaxHighlight(json) {
	if (typeof json != 'string') {
		json = JSON.stringify(json, undefined, 2);
	}
	json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
	return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
		var cls = 'number';
		if (/^"/.test(match)) {
			if (/:$/.test(match)) {
				cls = 'key';
			} else {
				cls = 'string';
			}
		} else if (/true|false/.test(match)) {
			cls = 'boolean';
		} else if (/null/.test(match)) {
			cls = 'null';
		}
		return '<span class="' + cls + '">' + match + '</span>';
	});
}

function setReturnType(selectedType){
	$("#returnTypeGrp").children().each(function (index){
		var $this = $(this);

		if($this.text() == selectedType){
			$this.addClass("active");
		} else {
			$this.removeClass("active");
		}
	});
}

function selectText(containerid) {
	if (document.selection) {
		var range = document.body.createTextRange();
		range.moveToElementText(document.getElementById(containerid));
		range.select();
	} else if (window.getSelection) {
		var range = document.createRange();
		range.selectNode(document.getElementById(containerid));
		window.getSelection().addRange(range);
	}
}

function toggleSubmitBtn(contentLength){
	console.log(contentLength);
	if (contentLength > 0){
		$("#queryBtn").prop("disabled", false);
	} else {
		$("#queryBtn").prop("disabled", true);
	}
}

$(function () {
	$("#returnTypeGrp").children().each(function (index){
		var $this = $(this);

		if(index == 0){
			$this.addClass("active");
		}

		$this.click(function(){
			setReturnType($this.text());
		})
	});

	$("#copyTxtBtn").hide();
	$("#copyTxtBtn").click(function(){
		selectText("result");
	});

	$("#result").hide();
	$("#messDiv").hide();

	$("#queryBtn").prop("disabled", true);
	$("#queryBtn").click(function(){
		runQuery();
	});

	if ($("#queryTA").val().length > 0){
		toggleSubmitBtn(1);
	}

	$("#queryTA").keyup(function(){
		toggleSubmitBtn(this.value.length);
	});

	var queries = availableRequests();
	var $ddl = $("#selReqDdl");

	for (var ind in queries){
		$ddl.append($("<option />").val(queries[ind].query).text(queries[ind].name));
	};

	$ddl.change(function(){
		if ($ddl.val() != "0") {
			$("#queryTA").val($ddl.val());
			toggleSubmitBtn(1);
		}
	});
});
