package se.lu.nateko.cp.meta.services.metaexport

import se.lu.nateko.cp.meta.core.data.DataObject
import java.time.Instant
import scala.quoted.Expr
import scala.quoted.Quotes


object Inspire{

	val RevisionDateTime = "2022-05-11T17:00:00+02:00"

	inline def templateCompileTimestamp: String = ${compilationTimestamp}
	private def compilationTimestamp(using Quotes): Expr[String] = Expr(Instant.now.toString)

}

class Inspire(dobj: DataObject) {
	export dobj.references.title

	def id: String = dobj.pid.fold(dobj.hash.id)(pid => s"info:hdl/$pid")

	def publication: Option[Instant] = dobj.submission.stop

	def creation: Instant = dobj.production.map(_.dateTime)
		.orElse(dobj.acquisition.flatMap(_.interval).map(_.stop))
		.getOrElse(dobj.submission.start)
}
