#!/usr/bin/env -S scala-cli shebang

//> using toolkit latest
//> using dep org.eclipse.rdf4j:rdf4j-rio-turtle:4.3.8
//> using dep org.eclipse.rdf4j:rdf4j-sail-memory:4.3.8
//> using dep org.eclipse.rdf4j:rdf4j-repository-sail:4.3.8
//> using dep com.github.scopt::scopt:4.1.0
//> using file "FDPClient.scala"

import java.io.{StringReader,StringWriter}
import os.*
import sttp.client4.quick.*
import sttp.model.Uri
import sttp.client4.Response
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.util.Values.iri
import org.eclipse.rdf4j.model.vocabulary.{RDF,DCAT,LDP}
import org.eclipse.rdf4j.rio.{Rio,RDFFormat}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import scopt.OParser


private val ShowMetadata = "showMetadata"
private val UploadL2Icos = "uploadL2ICOS"
private val UploadDatasetFromFile = "uploadDatasetFromFile"
private val DeleteAllDatasets = "deleteAllDatasets"

val sparqlEndpoint: Uri = uri"https://meta.icos-cp.eu/sparql"
val ConstructQueryFile = os.pwd / "src" / "main" / "scala-cli" / "fdp" / "resources" / "fdp.rq"

case class Config(
	host: String = "",
	command: String = "",
	path: String = "",
	catalog: String = "",
	nDatasets: Int = -1,
	inputTtlFile: String = "",
	queryFile: String = ""
)
val builder = OParser.builder[Config]
import builder.*
val hostParser = opt[String]("host").required().action((x, c) => c.copy(host = x)).text("URL of the FAIR Data Point")
val pathParser = opt[String]("path").required().action((x, c) => c.copy(path = x)).text("Path to the resource on the FAIR Data Point")
val catalogParser = opt[String]("catalog").required().action((x, c) => c.copy(catalog = x)).text("Catalog ID")
val nDatasetsParser = opt[Int]("nDatasets").action((x, c) => c.copy(nDatasets = x)).text("Number of datasets to upload")
val queryFileParser = opt[String]("inputTtlFile").action((x, c) => c.copy(inputTtlFile = x)).text("Path to the input Turtle file")
val parser = {
	OParser.sequence(
		programName("fdp"),
		help("help").text("Print this help text"),
		hostParser,
		cmd(ShowMetadata)
			.action((_, c) => c.copy(command = ShowMetadata))
			.text("Print the metadata of a resource")
			.children(pathParser),
		cmd(UploadL2Icos)
			.action((_, c) => c.copy(command = UploadL2Icos))
			.text("Upload metadata of ICOS L2 objects to a catalog")
			.children(catalogParser, nDatasetsParser),
		cmd(UploadDatasetFromFile)
			.action((_, c) => c.copy(command = UploadDatasetFromFile))
			.text("Upload metadata from a Turtle file")
			.children(catalogParser, queryFileParser),
		cmd(DeleteAllDatasets)
			.action((_, c) => c.copy(command = DeleteAllDatasets))
			.text("Delete all datasets from a catalog")
			.children(catalogParser)
	)
}

OParser.parse(parser, args, Config()) match
	case Some(config) =>
		config.command match
			case ShowMetadata => showMetadata(config)
			case UploadL2Icos => uploadL2ICOS(config)
			case UploadDatasetFromFile => uploadDatasetFromFile(config)
			case DeleteAllDatasets => deleteAllDatasets(config)
			case _ => println("Error: wrong usage\nTry --help for more information")
	case _ =>

def showMetadata(config: Config) =
	val uri = s"${config.host.toString()}${config.path.toString()}"
	val ttl = quickRequest.get(uri"$uri").send().body
	println(ttl)

def uploadL2ICOS(config: Config) =
	val fdp = FDPClient(uri"${config.host}")
	val catalogUri = uri"${config.host}/catalog/${config.catalog}"
	val constructQuery = os.read(ConstructQueryFile)
	val model = buildModel(constructQuery)
	val repo = createRepo(model)
	val conn = repo.getConnection()
	val limit = if config.nDatasets > 0 then s" LIMIT ${config.nDatasets}" else ""
	val datasetsQuery = s"SELECT ?subj WHERE { ?subj <${RDF.TYPE}> <${DCAT.DATASET}> }$limit"
	val datasetsRes = conn.prepareTupleQuery(datasetsQuery).evaluate()
	datasetsRes.forEach: bindings =>
		val dataset = uri"${bindings.getValue("subj").toString}"
		val fdpInputQuery = constructDatasetSparqlQuery(catalogUri, dataset)
		val writer = new StringWriter
		val turtleWriter = Rio.createWriter(RDFFormat.TURTLE, writer)
		val preparedQuery = conn.prepareGraphQuery(fdpInputQuery)
		preparedQuery.evaluate(turtleWriter)
		fdp.postAndPublishDatasets(writer.toString())
	datasetsRes.close()
	conn.close()

def uploadDatasetFromFile(config: Config) =
	val fdp = FDPClient(uri"${config.host}")
	val catalogUri = fdp.host.addPath(Seq("catalog", config.catalog))
	val ttl = os.read(stringToPath(config.inputTtlFile))
	fdp.postAndPublishDatasets(ttl)

def deleteAllDatasets(config: Config) =
	val fdp = FDPClient(uri"${config.host}")
	val uri = fdp.host.addPath(Seq("catalog", config.catalog)).addParam("format", "ttl")
	val ttl = quickRequest.get(uri).send().body
	val model = Rio.parse(StringReader(ttl), "", RDFFormat.TURTLE)
	val subj = iri(fdp.host.addPath(Seq("dataset", "")).toString())
	val datasets = model.getStatements(subj, LDP.CONTAINS, null).forEach: st =>
		val dataset = st.getObject().toString()
		fdp.deleteDataset(uri"$dataset")


def stringToPath(path: String): os.Path =
	if path.startsWith("/") then os.Path(path)
	else os.Path(os.RelPath(path), base = os.pwd)

def sparqlConstruct(query: String): String = sparql(query, "text/turtle")
def sparql(query: String, acceptType: String): String =
	val headers = Map("Accept" -> acceptType, "Content-Type" -> "text/plain")
	val resp: Response[String] = quickRequest.post(sparqlEndpoint).headers(headers).body(query).send()
	resp.body

def buildModel(query: String) =
	val ttlString = sparqlConstruct(query)
	Rio.parse(StringReader(ttlString), "", RDFFormat.TURTLE)

def createRepo(model: Model) =
	val repo = SailRepository(MemoryStore())
	val conn = repo.getConnection()
	conn.add(model)
	conn.close()
	repo


def getColumnsDatasetSparqlQuery(dataset: Uri): String =
	val datasetId = dataset.pathSegments.segments.last.v
	s"""
		|PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|
		|SELECT ?col
		|WHERE {
		|	VALUES ?dobj { <https://meta.icos-cp.eu/objects/$datasetId> }
		|	?dobj cpmeta:hasObjectSpec ?dobjSpec .
		|	?dobjSpec cpmeta:containsDataset ?dsSpec .
		|	?dsSpec cpmeta:hasColumn ?col .
		|}
	""".stripMargin

def constructDatasetSparqlQuery(catalog: Uri, dataset: Uri): String =
	s"""
		|PREFIX dcterms: <http://purl.org/dc/terms/>
		|PREFIX dcat: <http://www.w3.org/ns/dcat#>
		|PREFIX prov: <http://www.w3.org/ns/prov#>
		|PREFIX foaf: <http://xmlns.com/foaf/0.1/>
		|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
		|
		|CONSTRUCT{
		|	?ds ?pred ?obj .
		|	?ds dcterms:isPartOf <$catalog> .
		|	?ds dcterms:publisher <http://www.icos-cp.eu/> .
		|	<http://www.icos-cp.eu/> a foaf:Agent .
		|	<http://www.icos-cp.eu/> foaf:name "ICOS"^^xsd:string .
		|	?ds dcterms:hasVersion "1.0"^^xsd:string .
		|	?ds dcterms:temporal ?tempo .
		|	?tempo ?tempoPred ?tempoV .
		|	?ds dcat:distribution ?distro .
		|	?distro ?distroPred ?distroV .
		|	?distro dcat:mediaType "application/octet-stream"^^xsd:string .
		|}
		|WHERE {
		|	VALUES ?ds {<$dataset>}
		|	?ds ?pred ?obj .
		|	filter (?pred not in (dcterms:temporal, dcat:distribution))
		|	?ds dcterms:temporal ?tempo .
		|	?tempo ?tempoPred ?tempoV .
		|	?ds dcat:distribution ?distro .
		|	?distro ?distroPred ?distroV .
		|}
	""".stripMargin