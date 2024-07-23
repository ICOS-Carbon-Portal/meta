import sttp.model.Uri
import scala.language.dynamics
import sttp.client4.quick.*

class Row(vars: Map[String, Int], cells: Array[String]) extends Dynamic:
	def selectDynamic(field: String): String = cells(vars(field))

def sparqlSelect(endpoint: Uri, query: String): Seq[Row] =
	val qResp = quickRequest
		.post(endpoint)
		.header("Accept", "text/csv")
		//.header("Cache", "no-cache")
		.body(query)
		.send()
	if qResp.code.code != 200 then
		throw Error("SPARQL error:\n" + qResp.body)
	else
		val lines = qResp.body.linesIterator
		val vars = lines.next().split(",", -1).zipWithIndex.toMap
		lines
			.map: line =>
				Row(vars, line.split(",", -1))
			.toIndexedSeq