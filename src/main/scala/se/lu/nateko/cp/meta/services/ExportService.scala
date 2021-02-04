package se.lu.nateko.cp.meta.services

import java.net.URI
import org.eclipse.rdf4j.model.IRI
import spray.json._

import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.staticObjLandingPage
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.utils.rdf4j._


object ExportService{

	def dataObjs(sparqler: SparqlRunner)(implicit configs: EnvriConfigs, envri: Envri): Seq[URI] = {

		val metaItemPrefix = configs(envri).metaItemPrefix

		val specsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?spec
		|where{
		|	VALUES ?level { 2 3 }
		|	?spec cpmeta:hasDataLevel ?level .
		|	FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
		|	FILTER(STRSTARTS(str(?spec), "$metaItemPrefix"))
		|}""".stripMargin

		val specs: Iterator[String] = sparqler.evaluateTupleQuery(SparqlQuery(specsQuery)).flatMap(b =>
			Option(b.getValue("spec")).map(_.stringValue)
		)

		val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|prefix prov: <http://www.w3.org/ns/prov#>
		|select ?dobj where {
		|	VALUES ?spec {${specs.mkString("<", "> <", ">")}}
		|	?dobj cpmeta:hasObjectSpec ?spec .
		|	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
		|}
		|order by desc(?submTime)""".stripMargin

		sparqler
			.evaluateTupleQuery(SparqlQuery(query))
			.map(_.getValue("dobj"))
			.collect{case iri: IRI => iri.toJava}
			.toIndexedSeq
	}

	def schemaOrg(dobj: DataObject, handleProxies: HandleProxiesConfig)(implicit conf: EnvriConfig): JsObject = {

		val landingPage = JsString(staticObjLandingPage(dobj.hash)(conf).toString)

		val title: String = dobj.specificInfo.fold(
			l3 => l3.title,
			l2 => l2.acquisition.site match {
				case None => s"""${dobj.specification.self.label.getOrElse("")} from ${l2.acquisition.station.org.name}"""
				case Some(site) => s"""${dobj.specification.self.label.getOrElse("")} from ${site.location.flatMap(_.label).getOrElse("")} (${l2.acquisition.station.org.name})"""
			}
		)

		val description: JsValue = {
			val l3Descr = dobj.specificInfo.left.toSeq.flatMap(_.description)
			val specComments = dobj.specification.self.comments
			val prodComment = dobj.production.flatMap(_.comment)
			val citation = dobj.references.citationString
			val allDescrs = (l3Descr ++ specComments ++ prodComment ++ citation).map(_.trim).filter(_.length > 0)
			if(allDescrs.isEmpty) JsNull else JsString(allDescrs.mkString("\n"))
		}

		val identifier: JsValue = {
			val ids: Vector[JsString] = Vector(
				dobj.doi -> handleProxies.doi,
				dobj.pid -> handleProxies.basic
			).collect{
				case (Some(id), proxy) => JsString(proxy.toString + id)
			}
			ids match{
				case Vector() => JsNull
				case Vector(single) => single
				case _ => JsArray(ids)
			}
		}

		val published: JsValue = dobj.submission.stop.fold[JsValue](JsNull)(stop => JsString(stop.toString))

		val modified = JsString(
			(dobj.production.map(_.dateTime).toSeq :+ dobj.submission.start).sorted.head.toString
		)

		val keywords = dobj.keywords.fold[JsValue](JsNull)(kws => JsString(kws.mkString(", ")))

		JsObject(
			"@context"              -> JsString("https://schema.org"),
			"@type"                 -> JsString("Dataset"),
			"@id"                   -> landingPage,
			"name"                  -> JsString(title),
			"alternateName"         -> JsString(dobj.fileName),
			"description"           -> description,
			"url"                   -> landingPage,
			"identifier"            -> identifier,
			"inLanguage"            -> JsArray(
				JsObject(
					"@type" -> JsString("Language"),
					"name"  -> JsString("English")
				)
			),
			"includedInDataCatalog" -> JsObject(
				"@type" -> JsString("DataCatalog"),
				"name"  -> JsString(conf.dataHost)
			),
			"license"               -> JsString("https://creativecommons.org/licenses/by/4.0/"),
			"datePublished"         -> published,
			"dateModified"          -> modified,
			"keywords"              -> keywords
		)
	}

}
