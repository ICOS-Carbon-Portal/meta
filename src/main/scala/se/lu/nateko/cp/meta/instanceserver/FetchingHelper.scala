package se.lu.nateko.cp.meta.instanceserver

import java.time.Instant

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.utils.sesame._

trait FetchingHelper {
	protected def server: InstanceServer

	protected def getSingleUri(subj: IRI, pred: IRI): IRI =
		server.getUriValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalUri(subj: IRI, pred: IRI): Option[IRI] =
		server.getUriValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getLabeledResource(subj: IRI, pred: IRI): UriResource =
		getLabeledResource(getSingleUri(subj, pred))

	protected def getLabeledResource(uri: IRI) =
		UriResource(uri.toJava, label = getOptionalString(uri, RDFS.LABEL))

	protected def getOptionalString(subj: IRI, pred: IRI): Option[String] =
		server.getStringValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getSingleString(subj: IRI, pred: IRI): String =
		server.getStringValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getSingleInt(subj: IRI, pred: IRI): Int =
		server.getIntValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalInt(subj: IRI, pred: IRI): Option[Int] =
		server.getIntValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getOptionalDouble(subj: IRI, pred: IRI): Option[Double] =
		server.getDoubleValues(subj, pred, InstanceServer.AtMostOne).headOption

	protected def getSingleDouble(subj: IRI, pred: IRI): Double =
		server.getDoubleValues(subj, pred, InstanceServer.ExactlyOne).head

	protected def getOptionalInstant(subj: IRI, pred: IRI): Option[Instant] =
		server.getLiteralValues(subj, pred, XMLSchema.DATETIME, InstanceServer.AtMostOne).headOption.map(Instant.parse)

	protected def getSingleInstant(subj: IRI, pred: IRI): Instant =
		server.getLiteralValues(subj, pred, XMLSchema.DATETIME, InstanceServer.ExactlyOne).map(Instant.parse).head

	protected def getHashsum(dataObjUri: IRI, pred: IRI): Sha256Sum = {
		val hash: String = server.getLiteralValues(dataObjUri, pred, XMLSchema.BASE64BINARY, InstanceServer.ExactlyOne).head
		Sha256Sum.fromBase64(hash).get
	}

}