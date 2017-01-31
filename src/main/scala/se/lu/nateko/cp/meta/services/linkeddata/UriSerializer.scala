package se.lu.nateko.cp.meta.services.linkeddata

import java.net.{ URI => JavaUri }

import scala.Left
import scala.Right
import scala.collection.TraversableOnce.flattenTraversableOnce

import org.openrdf.model.Literal
import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.RDFS
import org.openrdf.query.BindingSet
import org.openrdf.query.QueryLanguage
import org.openrdf.repository.Repository

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.utils.sesame._
import views.html.ResourceViewInfo
import views.html.ResourceViewInfo.PropValue
import akka.http.scaladsl.model.HttpCharset
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.marshalling.Marshaller
import scala.concurrent.Future
import akka.http.scaladsl.marshalling.Marshalling.WithOpenCharset
import se.lu.nateko.cp.meta.api.CloseableIterator
import org.openrdf.model.Statement

trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
}

class SesameUriSerializer(repo: Repository) extends UriSerializer{
	import SesameUriSerializer._
	import InstanceServerSerializer.statementIterMarshaller

	val marshaller: ToResponseMarshaller[Uri] = {
		val htmlMarshaller: ToResponseMarshaller[Uri] = Marshaller(
			implicit exeCtxt => uri => Future.successful(
				WithOpenCharset(MediaTypes.`text/html`, getHtml(getViewInfo(uri, repo), _)) :: Nil
			)
		)
		val rdfMarshaller: ToResponseMarshaller[Uri] = statementIterMarshaller
			.compose(uri => () => getStatementsIter(uri, repo))

		Marshaller.oneOf(htmlMarshaller, rdfMarshaller)
	}

	private def getHtml(viewInfo: ResourceViewInfo, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			views.html.UriResourcePage(viewInfo).body
		)
	)
}

private object SesameUriSerializer{

	def getStatementsIter(res: Uri, repo: Repository): CloseableIterator[Statement] = {
		val uri = repo.getValueFactory.createURI(res.toString)
		val own = repo.access(conn => conn.getStatements(uri, null, null, false))
		val about = repo.access(conn => conn.getStatements(null, null, uri, false))
		own ++ about
	}

	def getViewInfo(res: Uri, repo: Repository): ResourceViewInfo = repo.accessEagerly{conn =>

		val propInfo = conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(res)).evaluate()

		val propInfos = new SesameIterationIterator(propInfo).map{bset =>

			val propUriOpt: Option[UriResource] = getOptUriRes(bset, "prop", "propLabel")

			val propValueOpt: Option[PropValue] = bset.getValue("val") match {
				case uri: URI =>
					val valLabel = getOptLit(bset, "valLabel")
					Some(Left(UriResource(uri, valLabel)))
				case lit: Literal =>
					Some(Right(lit.stringValue))
				case _ => None
			}
			propUriOpt zip propValueOpt
		}.flatten.toIndexedSeq

		val usageInfo = conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(res)).evaluate()

		val usageInfos = new SesameIterationIterator(usageInfo).map{bset =>
			getOptUriRes(bset, "obj", "objLabel") zip getOptUriRes(bset, "prop", "propLabel")
		}.flatten.toIndexedSeq

		val uri = JavaUri.create(res.toString)
		val seed = ResourceViewInfo(UriResource(uri, None), None, Nil, Nil, usageInfos)

		propInfos.foldLeft(seed)((acc, propAndVal) => propAndVal match {

			case (UriResource(propUri, _), Right(strVal)) if(propUri == sesameUriToJava(RDFS.LABEL)) =>
				acc.copy(res = UriResource(uri, Some(strVal)))

			case (UriResource(propUri, _), Right(strVal)) if(propUri == sesameUriToJava(RDFS.COMMENT)) =>
				acc.copy(comment = Some(strVal))

			case (UriResource(propUri, _), Left(rdfType)) if(propUri == sesameUriToJava(RDF.TYPE)) =>
				acc.copy(types = rdfType :: acc.types)

			case _ =>
				acc.copy(propValues = propAndVal :: acc.propValues)
		})
	}

	private def getOptUriRes(bset: BindingSet, varName: String, lblName: String): Option[UriResource] = {
		bset.getValue(varName) match {
			case uri: URI =>
				val label = getOptLit(bset, lblName)
				Some(UriResource(uri, label))
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
