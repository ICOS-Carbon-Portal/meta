package se.lu.nateko.cp.meta.instanceserver

import java.net.URI
import java.time.{Instant, LocalDate}

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.utils.parseInstant
import se.lu.nateko.cp.meta.utils.rdf4j.*


trait FetchingHelper {
	def server: InstanceServer

	protected def getSingleUri(subj: IRI, pred: IRI): IRI =
		server.getUriValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalUri(subj: IRI, pred: IRI): Option[IRI] =
		server.getUriValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getLabeledResource(subj: IRI, pred: IRI): UriResource =
		getLabeledResource(getSingleUri(subj, pred))

	def getLabeledResource(uri: IRI) =
		UriResource(uri.toJava, getOptionalString(uri, RDFS.LABEL), server.getStringValues(uri, RDFS.COMMENT))

	protected def getOptionalString(subj: IRI, pred: IRI): Option[String] =
		server.getStringValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getSingleString(subj: IRI, pred: IRI): String =
		server.getStringValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getSingleInt(subj: IRI, pred: IRI): Int =
		server.getIntValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalInt(subj: IRI, pred: IRI): Option[Int] =
		server.getIntValues(subj, pred, InstanceServer.AtMostOne).headOption

	//TODO Go back to at most one in this method, or rework the whole cardinality validation approach
	protected def getOptionalLong(subj: IRI, pred: IRI): Option[Long] =
		server.getLongValues(subj, pred, InstanceServer.Default).headOption

	protected def getOptionalDouble(subj: IRI, pred: IRI): Option[Double] =
		server.getDoubleValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getOptionalFloat(subj: IRI, pred: IRI): Option[Float] =
		server.getFloatValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getOptionalBool(subj: IRI, pred: IRI): Option[Boolean] =
		server.getLiteralValues(subj, pred, XSD.BOOLEAN, InstanceServer.AtMostOne).headOption.map(_.toLowerCase == "true")

	protected def getSingleDouble(subj: IRI, pred: IRI): Double =
		server.getDoubleValues(subj, pred, InstanceServer.ExactlyOne).head

	def getOptionalInstant(subj: IRI, pred: IRI): Option[Instant] =
		server.getLiteralValues(subj, pred, XSD.DATETIME, InstanceServer.AtMostOne).headOption.map(parseInstant)

	protected def getSingleInstant(subj: IRI, pred: IRI): Instant =
		server.getLiteralValues(subj, pred, XSD.DATETIME, InstanceServer.ExactlyOne).map(parseInstant).head

	protected def getOptionalLocalDate(subj: IRI, pred: IRI): Option[LocalDate] =
		server.getLiteralValues(subj, pred, XSD.DATE, InstanceServer.AtMostOne).headOption.map(LocalDate.parse)

	protected def getSingleUriLiteral(subj: IRI, pred: IRI): URI =
		server.getUriLiteralValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalUriLiteral(subj: IRI, pred: IRI): Option[URI] =
		server.getUriLiteralValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getHashsum(dataObjUri: IRI, pred: IRI): Sha256Sum = {
		val hash: String = server.getLiteralValues(dataObjUri, pred, XSD.BASE64BINARY, InstanceServer.ExactlyOne).head
		Sha256Sum.fromBase64(hash).get
	}

}

object FetchingHelper{
	def apply(instServer: InstanceServer): FetchingHelper = new FetchingHelper{
		override def server = instServer
	}
}
