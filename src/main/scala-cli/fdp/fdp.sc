#!/usr/bin/env -S scala-cli shebang

//> using toolkit default
//> using dep org.eclipse.rdf4j:rdf4j-rio-turtle:4.3.8
//> using dep org.eclipse.rdf4j:rdf4j-sail-memory:4.3.8
//> using dep org.eclipse.rdf4j:rdf4j-repository-sail:4.3.8
//> using dep com.github.scopt::scopt:4.1.0
//> using file "FDPClient.scala"

import java.io.{StringReader,StringWriter}
import sttp.client4.quick.*
import sttp.model.Uri
import sttp.client4.Response
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.util.Values.iri
import org.eclipse.rdf4j.model.vocabulary.{RDF,DCAT,LDP}
import org.eclipse.rdf4j.rio.{Rio,RDFFormat}
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import scopt.OParser
import scala.util.Using


val ShowMetadata = "showMetadata"
val UploadL2Icos = "uploadL2ICOS"
val UploadDatasetFromFile = "uploadDatasetFromFile"
val DeleteAllDatasets = "deleteAllDatasets"

val sparqlEndpoint: Uri = uri"https://meta.icos-cp.eu/sparql"
val ConstructQueryFile = os.pwd / os.RelPath("src/main/scala-cli/fdp/resources/secondEcvDemoImport.rq")

case class Config(
	host: String = "",
	command: String = "",
	path: String = "",
	catalog: String = "",
	nDatasets: Int = -1,
	inputTtlFile: String = "",
	queryFile: String = "",
	dryRun: Boolean = false,
	username: Option[String] = None,
	password: Option[String] = None
)
val parser =
	val builder = OParser.builder[Config]
	import builder.*
	val hostParser = opt[String]("host")
		.text("URL of the FAIR Data Point")
		.action((x, c) => c.copy(host = x))
		.required()
	val userParser = opt[String]("user")
		.text("Username for an account with needed permissions")
		.action((u, c) => c.copy(username = Some(u)))
	val passwordParser = opt[String]("password")
		.text("Password that goes with the username")
		.action((p, c) => c.copy(password = Some(p)))
	val pathParser = opt[String]("path")
		.text("Path to the resource on the FAIR Data Point")
		.action((x, c) => c.copy(path = x))
		.required()
	val catalogParser = opt[String]("catalog")
		.text("Catalog ID")
		.required()
		.action((x, c) => c.copy(catalog = x))
	val nDatasetsParser = opt[Int]("limit")
		.text("Max number of datasets to upload")
		.action((x, c) => c.copy(nDatasets = x))
	val dryRunParser = opt[Boolean]("dry-run")
		.text("Print metadata instead of publishing")
		.action((dr, c) => c.copy(dryRun = dr))
	val ttlFileParser = opt[String]("inputTtlFile")
		.text("Path to the input Turtle file")
		.action((x, c) => c.copy(inputTtlFile = x))
	OParser.sequence(
		programName("fdp"),
		help("help").text("Print this help text"),
		hostParser,
		userParser,
		passwordParser,
		cmd(ShowMetadata)
			.text("Print the metadata of a resource")
			.action((_, c) => c.copy(command = ShowMetadata))
			.children(pathParser),
		cmd(UploadL2Icos)
			.text("Upload metadata of ICOS L2 objects to a catalog")
			.action((_, c) => c.copy(command = UploadL2Icos))
			.children(catalogParser, nDatasetsParser, dryRunParser),
		cmd(UploadDatasetFromFile)
			.text("Upload metadata from a Turtle file")
			.action((_, c) => c.copy(command = UploadDatasetFromFile))
			.children(catalogParser, ttlFileParser),
		cmd(DeleteAllDatasets)
			.text("Delete all datasets from a catalog")
			.action((_, c) => c.copy(command = DeleteAllDatasets))
			.children(catalogParser)
	)
end parser

OParser.parse(parser, args, Config()) match
	case Some(config) =>
		config.command match
			case ShowMetadata => showMetadata(config)
			case UploadL2Icos => uploadL2ICOS(config)
			case UploadDatasetFromFile => uploadDatasetFromFile(config)
			case DeleteAllDatasets => deleteAllDatasets(config)
			case _ => println("Error: wrong usage\nTry --help for more information")
	case _ =>

def initFdp(config: Config): FDPClient =
	val hostUri = uri"${config.host}"
	val fromOptions =
		for user <- config.username; pass <- config.password
		yield FDPClient(hostUri, User(user, pass))
	fromOptions.getOrElse:
		FDPClient.interactiveInit(hostUri)

def showMetadata(config: Config) =
	val uri = s"${config.host.toString()}${config.path.toString()}"
	val ttl = quickRequest.get(uri"$uri").send().body
	println(ttl)

def uploadL2ICOS(config: Config) =
	val fdp = initFdp(config)
	val catalogUri = uri"${config.host}/catalog/${config.catalog}"
	val constructQuery = os.read(ConstructQueryFile)
	val model = importFromIcos(constructQuery)
	val repo = createRepo(model)
	Using.Manager: use =>
		val conn = use(repo.getConnection())
		val limit = if config.nDatasets > 0 then s" LIMIT ${config.nDatasets}" else ""
		val datasetsQuery = s"SELECT ?dataset WHERE { ?dataset a <${DCAT.DATASET}> }$limit"
		val datasetsResult = use(conn.prepareTupleQuery(datasetsQuery).evaluate())
		datasetsResult.forEach: bindings =>
			val dataset = uri"${bindings.getValue("dataset").toString}"
			val datasetQuery = constructDatasetSparqlQuery(catalogUri, dataset)
			val dsTurtle: String = evaluateGraphQuery(conn, datasetQuery)
			val distributionQuery = constructDistributionSparqlQuery(dataset)
			val distTurtle: String = evaluateGraphQuery(conn, distributionQuery)
			if config.dryRun then
				println(s"Dataset:\n$dsTurtle\n\nDistribution:\n$distTurtle\n\n\n")
			else
				fdp.postAndPublishDatasets(dsTurtle)
				fdp.postAndPublishDistributions(distTurtle)

def uploadDatasetFromFile(config: Config) =
	val fdp = initFdp(config)
	val catalogUri = fdp.host.addPath(Seq("catalog", config.catalog))
	val ttl = os.read(stringToPath(config.inputTtlFile))
	fdp.postAndPublishDatasets(ttl)

def deleteAllDatasets(config: Config): Unit =
	val fdp = initFdp(config)
	val uri = fdp.host.addPath(Seq("catalog", config.catalog)).addParam("format", "ttl")
	val ttl = quickRequest.get(uri).send().body
	val model = Rio.parse(StringReader(ttl), "", RDFFormat.TURTLE)
	val subj = iri(fdp.host.addPath(Seq("dataset", "")).toString())
	model.getStatements(subj, LDP.CONTAINS, null).forEach: st =>
		val dataset = st.getObject().toString()
		fdp.deleteDataset(uri"$dataset")

def evaluateGraphQuery(conn: RepositoryConnection, query: String): String =
	val writer = new StringWriter
	val turtleWriter = Rio.createWriter(RDFFormat.TURTLE, writer)
	val preparedQuery = conn.prepareGraphQuery(query)
	preparedQuery.evaluate(turtleWriter)
	writer.toString

def stringToPath(path: String): os.Path =
	if path.startsWith("/") then os.Path(path)
	else os.Path(os.RelPath(path), base = os.pwd)

def sparqlConstruct(query: String): String = sparql(query, "text/turtle")

def sparql(query: String, acceptType: String): String =
	val headers = Map("Accept" -> acceptType, "Content-Type" -> "text/plain")
	val resp: Response[String] = quickRequest.post(sparqlEndpoint).headers(headers).body(query).send()
	resp.body

def importFromIcos(query: String): Model =
	val ttlString = sparqlConstruct(query)
	Rio.parse(StringReader(ttlString), "", RDFFormat.TURTLE)

def createRepo(model: Model): SailRepository =
	val repo = SailRepository(MemoryStore())
	Using(repo.getConnection()): conn =>
		conn.add(model)
		repo
	.get


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
		|	?ds dcterms:publisher <https://www.icos-cp.eu/> .
		|	<https://www.icos-cp.eu/> a foaf:Agent .
		|	<https://www.icos-cp.eu/> foaf:name "ICOS"^^xsd:string .
		|	?ds dcterms:hasVersion "1.0"^^xsd:string .
		|	?ds dcterms:temporal ?tempo .
		|	?tempo ?tempoPred ?tempoV .
		|	#?ds dcat:distribution ?distro .
		|	#?distro ?distroPred ?distroV .
		|	#?distro dcat:mediaType "application/octet-stream"^^xsd:string .
		|}
		|WHERE {
		|	VALUES ?ds {<$dataset>}
		|	?ds ?pred ?obj .
		|	filter (?pred not in (dcterms:temporal, dcat:distribution, dcterms:isPartOf))
		|	?ds dcterms:temporal ?tempo .
		|	?tempo ?tempoPred ?tempoV .
		|	#?ds dcat:distribution ?distro .
		|	#?distro ?distroPred ?distroV .
		|}
	""".stripMargin

def constructDistributionSparqlQuery(dataset: Uri): String =
	s"""
	""".stripMargin
