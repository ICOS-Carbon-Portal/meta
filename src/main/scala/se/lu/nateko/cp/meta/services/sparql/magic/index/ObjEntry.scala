package se.lu.nateko.cp.meta.services.sparql.magic.index

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.sparql.magic.ObjInfo

import java.time.Instant
import scala.compiletime.uninitialized

final class ObjEntry(val hash: Sha256Sum, val idx: Int, var prefix: String) extends ObjInfo with Serializable {
	var spec: IRI | Null = uninitialized
	var submitter: IRI | Null = uninitialized
	var station: IRI | Null = uninitialized
	var site: IRI | Null = uninitialized
	var size: Long = -1
	var fName: String | Null = ""
	var samplingHeight: Float = Float.NaN
	var dataStart: Long = Long.MinValue
	var dataEnd: Long = Long.MinValue
	var submissionStart: Long = Long.MinValue
	var submissionEnd: Long = Long.MinValue
	var isNextVersion: Boolean = false

	private final def dateTimeFromLong(dt: Long): Option[Instant] =
		if (dt == Long.MinValue) None
		else Some(Instant.ofEpochMilli(dt).nn)

	def sizeInBytes: Option[Long] = if (size >= 0) Some(size) else None
	def fileName: Option[String] = {
		if (fName == null) { None }
		else { Some(fName.nn) }
	}
	def samplingHeightMeters: Option[Float] = if (samplingHeight == Float.NaN) None else Some(samplingHeight)
	def dataStartTime: Option[Instant] = dateTimeFromLong(dataStart)
	def dataEndTime: Option[Instant] = dateTimeFromLong(dataEnd)
	def submissionStartTime: Option[Instant] = dateTimeFromLong(submissionStart)
	def submissionEndTime: Option[Instant] = dateTimeFromLong(submissionEnd)

	def uri(factory: ValueFactory): IRI = factory.createIRI(prefix + hash.base64Url).nn
}
