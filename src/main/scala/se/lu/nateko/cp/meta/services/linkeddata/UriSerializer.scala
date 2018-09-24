package se.lu.nateko.cp.meta.services.linkeddata

import java.net.{ URI => JavaUri }

import scala.Left
import scala.Right
import scala.collection.TraversableOnce.flattenTraversableOnce
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling.WithFixedContentType
import akka.http.scaladsl.marshalling.Marshalling.WithOpenCharset
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharset
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers
import se.lu.nateko.cp.meta.utils.rdf4j._
import views.html.ResourceViewInfo
import views.html.ResourceViewInfo.PropValue

trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
}

class Rdf4jUriSerializer(repo: Repository, servers: DataObjectInstanceServers)(implicit envries: EnvriConfigs) extends UriSerializer{
	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer._

	val marshaller: ToResponseMarshaller[Uri] = {

		val htmlOrJsonMarshaller: ToResponseMarshaller[Uri] = Marshaller(
			implicit exeCtxt => uri => getJsonFetcher(uri).map{jsonFetcherOpt =>
				WithOpenCharset(MediaTypes.`text/html`, getHtml(uri)) ::
				jsonFetcherOpt.toList.map(WithFixedContentType(ContentTypes.`application/json`, _))
			}
		)
		val rdfMarshaller: ToResponseMarshaller[Uri] = statementIterMarshaller
			.compose(uri => () => getStatementsIter(uri, repo))

		Marshaller.oneOf(htmlOrJsonMarshaller, rdfMarshaller)
	}

	private def inferEnvri(uri: Uri) = Envri.infer(new java.net.URI(uri.toString)).getOrElse(
		throw new MetadataException("Could not infer ENVRI from URL " + uri.toString)
	)

	private def getHtml(uri: Uri)(charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			views.html.UriResourcePage(getViewInfo(uri, repo, inferEnvri(uri))).body
		)
	)

	private def getJsonFetcher(uri: Uri)(implicit ctxt: ExecutionContext): Future[Option[() => HttpResponse]] = Future{
		import servers.metaVocab
		val subj = metaVocab.factory.createIRI(uri.toString)

		val isObjSpec = repo.accessEagerly{ implicit conn =>
			conn.getStatements(subj, RDF.TYPE, null, false).asScalaIterator.exists{st =>
				isA(st.getObject, metaVocab.dataObjectSpecClass)
			}
		}

		if(isObjSpec){
			implicit val envri = inferEnvri(uri)
			import se.lu.nateko.cp.meta.core.data.JsonSupport.dataObjectSpecFormat
			import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling.getJson
			Some(() => getJson(servers.getDataObjSpecification(subj).toOption))
		}else
			None
	}

	private def isA(subClass: Value, superClass: IRI)(implicit conn: RepositoryConnection): Boolean = subClass match {
		case sub: Resource =>
			superClass == sub || conn.getStatements(sub, RDFS.SUBCLASSOF, null, false).asScalaIterator.exists{
				st => isA(st.getObject, superClass)
			}
		case _ => false
	}
}



private object Rdf4jUriSerializer{

	val Limit = 500

	def getStatementsIter(res: Uri, repo: Repository): CloseableIterator[Statement] = {
		val uri = repo.getValueFactory.createIRI(res.toString)
		val own = repo.access(conn => conn.getStatements(uri, null, null, false))
		val about = repo.access(conn => conn.getStatements(null, null, uri, false))
		own ++ about
	}

	def getViewInfo(res: Uri, repo: Repository, envri: Envri.Value): ResourceViewInfo = repo.accessEagerly{conn =>

		val propInfo = conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(res)).evaluate()

		val propInfos = new Rdf4jIterationIterator(propInfo).map{bset =>

			val propUriOpt: Option[UriResource] = getOptUriRes(bset, "prop", "propLabel")

			val propValueOpt: Option[PropValue] = bset.getValue("val") match {
				case uri: IRI =>
					val valLabel = getOptLit(bset, "valLabel")
					Some(Left(UriResource(uri.toJava, valLabel)))
				case lit: Literal =>
					Some(Right(lit.stringValue))
				case _ => None
			}
			propUriOpt zip propValueOpt
		}.flatten.take(Limit).toIndexedSeq

		val usageInfo = conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(res)).evaluate()

		val usageInfos = new Rdf4jIterationIterator(usageInfo).map{bset =>
			getOptUriRes(bset, "obj", "objLabel") zip getOptUriRes(bset, "prop", "propLabel")
		}.flatten.take(Limit).toIndexedSeq

		val uri = JavaUri.create(res.toString)
		val seed = ResourceViewInfo(UriResource(uri, None), envri, None, Nil, Nil, usageInfos)

		propInfos.foldLeft(seed)((acc, propAndVal) => propAndVal match {

			case (UriResource(propUri, _), Right(strVal)) if(propUri === RDFS.LABEL) =>
				acc.copy(res = UriResource(uri, Some(strVal)))

			case (UriResource(propUri, _), Right(strVal)) if(propUri === RDFS.COMMENT) =>
				acc.copy(comment = Some(strVal))

			case (UriResource(propUri, _), Left(rdfType)) if(propUri === RDF.TYPE) =>
				acc.copy(types = rdfType :: acc.types)

			case _ =>
				acc.copy(propValues = propAndVal :: acc.propValues)
		})
	}

	private def getOptUriRes(bset: BindingSet, varName: String, lblName: String): Option[UriResource] = {
		bset.getValue(varName) match {
			case uri: IRI =>
				val label = getOptLit(bset, lblName)
				Some(UriResource(uri.toJava, label))
			case _ => None
		}
	}

	private def getOptLit(bset: BindingSet, varName: String): Option[String] = {
		bset.getValue(varName) match {
			case null => None
			case lit: Literal => Some(lit.stringValue)
			case _ => None
		}
	}

	def resourceViewInfoQuery(res: Uri) =
		s"""SELECT ?prop ?propLabel ?val ?valLabel
		|WHERE{
		|	<${res.toString}> ?prop ?val .
		|	OPTIONAL {?prop rdfs:label ?propLabel}
		|	OPTIONAL {?val rdfs:label ?valLabel}
		|}""".stripMargin

	def resourceUsageInfoQuery(res: Uri) =
		s"""SELECT ?obj ?objLabel ?prop ?propLabel
		|WHERE{
		|	?obj ?prop <${res.toString}> .
		|	OPTIONAL {?obj rdfs:label ?objLabel}
		|	OPTIONAL {?prop rdfs:label ?propLabel}
		|}""".stripMargin

}
