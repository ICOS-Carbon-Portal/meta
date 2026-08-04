package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, RdfUpdate, TriplestoreConnection}
import se.lu.nateko.cp.meta.persistence.RdfHistoryClient

import java.time.Instant
import scala.concurrent.Future

object LabelingDb:

	opaque type ProvConn <: TriplestoreConnection = TriplestoreConnection
	opaque type LblAppConn <: TriplestoreConnection = TriplestoreConnection
	opaque type IcosConn <: TriplestoreConnection = TriplestoreConnection

	private def asProv(conn: TriplestoreConnection): ProvConn = conn
	private def asLbl(conn: TriplestoreConnection): LblAppConn = conn
	private def asIcos(conn: TriplestoreConnection): IcosConn = conn

class LabelingDb(
	provServer: InstanceServer,
	lblServer: InstanceServer,
	icosServer: InstanceServer,
	historyClient: RdfHistoryClient
):
	import LabelingDb.*

	def history: Future[Seq[(Instant, RdfUpdate)]] =
		historyClient.history(Seq(provServer.writeContext, lblServer.writeContext))

	def accessProv[T](reader: ProvConn ?=> T): T = provServer.access: conn ?=>
		reader(using asProv(conn))

	def provView(using conn: TriplestoreConnection): ProvConn = asProv:
		conn.withContexts(provServer.writeContext, provServer.readContexts)

	def accessLbl[T](reader: LblAppConn ?=> T): T = lblServer.access: conn ?=>
		reader(using asLbl(conn))

	def accessIcos[T](reader: IcosConn ?=> T): T = icosServer.access: conn ?=>
		reader(using asIcos(conn))

	def applyLblDiff(from: Seq[Statement], to: Seq[Statement]): Unit =
		lblServer.applyDiff(from, to)

	def applyProvDiff(from: Seq[Statement], to: Seq[Statement]): Unit =
		provServer.applyDiff(from, to)
