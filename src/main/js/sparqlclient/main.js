import queries from './queries';
import { prefixes } from './prefixes';

const txtReturnType = 'TSV or Turtle';

function getMimeType(selectedReturnType) {
	switch (selectedReturnType) {
		case "JSON":
			return "application/json";

		case "CSV":
			return "text/csv";

		case "XML":
			return "application/xml";

		case txtReturnType:
			return "text/plain";
	}
}

function postProcessing(result, selectedReturnType, requestTime){
	var highlightTime = 0;
	if (selectedReturnType == "JSON") {
		var highlightStart = new Date().getTime();
		$("#result").html(syntaxHighlight(result));
		highlightTime = new Date().getTime() - highlightStart;

	} else if (selectedReturnType == txtReturnType) {
		$("#result").html(result.replace(/\</g,"&lt;"));

	} else if (selectedReturnType == "XML") {
		let resultStr = new XMLSerializer().serializeToString(result.documentElement);
		$("#result").html(syntaxHighlight(resultStr));

	} else {
		$("#result").html(result);
	}

	if (selectedReturnType == "JSON") {
		$("#statusSpan").text(
			result.results.bindings.length + " bindings returned (" +
			requestTime + " ms request, " + highlightTime + " ms styling)."
		);
		
	} else if (result instanceof XMLDocument) {
		$("#statusSpan").text(
			result.getElementsByTagName("results")[0].childElementCount + " records returned (" + requestTime + " ms request)."
		);

	} else if (selectedReturnType == "XML") {
		$("#statusSpan").text(
			result.split(/\n/).length - 2 + " records returned (" + requestTime + " ms request)."
		);

	} else {
		$("#statusSpan").text(
			result.split(/\n/).length - 2 + " rows returned (" + requestTime + " ms request)."
		);
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

function processQuery(paramsEnc){
	// paramsEnc starts with "?"
	var params = decodeURI(paramsEnc.substring(1));
	var pairsArr = params.split("&");
	var query = {};

	pairsArr.forEach(function(pair){
		var keyVal = pair.split("=");
		query[keyVal[0]] = keyVal[1];
	});

	return query;
}

function updateUrlBar(state){
	var query = "?";

	if(state.q !== undefined){
		query += "q=" + state.q + "&";
	}

	query += "type=" + state.type;

	history.pushState(state, "", query);
};

function initRequestDdl(queries, queryParams, state){
	var $ddl = $("#selReqDdl");

	for (var ind in queries){
		$ddl.append($("<option />").val(queries[ind].query).text(queries[ind].name));
	};

	$ddl.change(function(){
		if ($ddl.val() != "0") {
			window.yasqe.setValue($ddl.val());

			state.q = $ddl.children("option").filter(":selected").text();
			updateUrlBar(state);
		}
	});

	if (queryParams.q !== undefined){
		$ddl.children("option").each(function() {
			if($(this).text() == queryParams.q) {
				$(this).attr('selected', 'selected');
				$ddl.change();
				state.q = queryParams.q;
			}
		});
	}

	return state;
}

function setReturnType($btns, selectedType){
	$btns.children().each(function (index){
		var $this = $(this);

		if($this.text() == selectedType){
			$this.addClass("active");
		} else {
			$this.removeClass("active");
		}
	});
}

function initReturnTypeBtns(queryParams, state){
	var $btns = $("#returnTypeGrp");

	$btns.children().each(function (index){
		var $this = $(this);

		if(index == 1){
			$this.addClass("active");
			state.type = $this.text();
		}

		$this.click(function(){
			setReturnType($btns, $this.text());
		})
	});

	$btns.children().each(function(ind, child){
		$(child).click(function(){
			state.type = $(child).text();
			updateUrlBar(state);
		});
	});

	if (queryParams.type !== undefined){
		$btns.children().each(function(ind, child){
			$(child).removeClass("active");

			if ($(child).text() == queryParams.type){
				$(child).addClass("active");
				state.type = queryParams.type;
			}
		});
	}

	updateUrlBar(state);
}

$(function () {
	$("#copyTxtBtn").hide();
	$("#copyTxtBtn").click(function(){
		selectText("result");
	});

	$("#result").hide();
	$("#messDiv").css({opacity: 0});

	var queryParams = processQuery(window.location.search);
	var state = {
		q: undefined,
		type: undefined
	};

	// https://github.com/TriplyDB/Yasgui
	// https://triply.cc/docs/yasgui-api#yasqe-api
	// http://biblio.inf.ufsc.br/sparql2/YASGUI.YASQE-gh-pages/doc/
	// https://codemirror.net/doc/manual.html

	// Use our own prefixes
	Yasqe.defaults.autocompleters.splice(Yasqe.defaults.autocompleters.indexOf("prefixes"), 1);
	Yasqe.forkAutocompleter("prefixes", {
		name: "prefixes-local",
		persistenceId: null,
		get: prefixes
	});

	const yasqeConf = {
		editorHeight: "auto",
		autocapitalize: false,
		indentWithTabs: true,
		indentUnit: 4,
		requestConfig: {
			endpoint: "/sparql",
			acceptHeaderSelect: () => getMimeType(state.type)
		}
	};
	window.yasqe = new Yasqe(document.getElementById("yasqe"), yasqeConf);

	function getResult(response) {
		if (state.type === "JSON") {
			return response.body;

		} else if (state.type === "XML") {
			return $.parseXML(response.text)

		} else {
			return response.text;
		}
	}

	window.yasqe.on("queryResponse", function (yasqe, response, duration) {
		
		$("#result").show();

		if (response.ok) {
			$("#copyTxtBtn").show();

			postProcessing(getResult(response), state.type, duration);

			$("#messSpan").text("Query was successful").css({ opacity: 1 }).delay(3000).fadeTo(1000, 0);
		
		} else {
			$("#statusSpan").text("");
			$("#messSpan").text("Could not execute query").css({ opacity: 1 }).delay(3000).fadeTo(1000, 0);
			$("#result").html(response.response.text);
		}
	});

	window.yasqe.setValue(document.getElementById("hiddenQuery").value);

	state = initRequestDdl(queries, queryParams, state);
	initReturnTypeBtns(queryParams, state);
});

