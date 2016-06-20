package se.lu.nateko.cp.meta.instanceserver

import java.time.Instant

import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDFS
import org.openrdf.model.vocabulary.XMLSchema

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.utils.sesame._

trait FetchingHelper {
	protected def server: InstanceServer

	protected def getSingleUri(subj: URI, pred: URI): URI =
		server.getUriValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalUri(subj: URI, pred: URI): Option[URI] =
		server.getUriValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getLabeledResource(subj: URI, pred: URI): UriResource =
		getLabeledResource(getSingleUri(subj, pred))

	protected def getLabeledResource(uri: URI) =
		UriResource(uri, label = getOptionalString(uri, RDFS.LABEL))

	protected def getOptionalString(subj: URI, pred: URI): Option[String] =
		server.getStringValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getSingleString(subj: URI, pred: URI): String =
		server.getStringValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getSingleInt(subj: URI, pred: URI): Int =
		server.getIntValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalDouble(subj: URI, pred: URI): Option[Double] =
		server.getDoubleValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getSingleDouble(subj: URI, pred: URI): Double =
		server.getDoubleValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalInstant(subj: URI, pred: URI): Option[Instant] =
		server.getLiteralValues(subj, pred, XMLSchema.DATETIME, InstanceServer.AtMostOne).headOption.map(Instant.parse)

	protected def getSingleInstant(subj: URI, pred: URI): Instant =
		server.getLiteralValues(subj, pred, XMLSchema.DATETIME, InstanceServer.ExactlyOne).map(Instant.parse).head

	protected def getHashsum(dataObjUri: URI, pred: URI): Sha256Sum = {
		val hash: String = server.getLiteralValues(dataObjUri, pred, XMLSchema.BASE64BINARY, InstanceServer.ExactlyOne).head
		Sha256Sum.fromBase64(hash).get
	}

}