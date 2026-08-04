package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import spray.json.*

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

/**
 * Reads through RDF4J's remote repository, but sends logical InstanceServer
 * write batches to rdfStore so it can use the original LoggingInstanceServer
 * transaction boundary locally.
 */
final class RemoteRdf4jInstanceServer(
	repo: Repository,
	read: Seq[IRI],
	write: IRI,
	mutationEndpoint: URI
)(using system: ActorSystem) extends Rdf4jInstanceServer(repo, read, write):

	override def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit] = Try:
		import RdfMutation.given
		val mutation = RdfMutation.from(writeContext, updates).toJson.compactPrint
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = mutationEndpoint.toString,
			entity = HttpEntity(ContentTypes.`application/json`, mutation)
		)
		val response = Await.result(Http().singleRequest(request), Duration.Inf)
		val body = Await.result(response.entity.toStrict(60.seconds), 60.seconds).data.utf8String
		if !response.status.isSuccess then throw RuntimeException(
			s"rdfStore instance update failed with ${response.status}: $body"
		)
		cotransact

	override def withContexts(read: Seq[IRI], write: IRI): InstanceServer =
		RemoteRdf4jInstanceServer(repo, read, write, mutationEndpoint)

end RemoteRdf4jInstanceServer
