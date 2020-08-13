package se.lu.nateko.cp.meta.upload

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import java.net.URI
import scala.concurrent.Future
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.utils.async.{ok, error, executeSequentially}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scala.io.Source
import scala.util.Success
import scala.util.Failure
import spray.json._
import akka.Done
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.RequestEntity
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

object L3UpdateWorkbench extends CpmetaJsonProtocol{
	implicit val system = ActorSystem("l3update_workbench")
	import system.dispatcher

	val uploadConfBase = new CpUploadClient.Config(
		"???",
		"meta.icos-cp.eu",
		"data.icos-cp.eu"
		//Some(Uri("http://127.0.0.1:9094")),
		//Some(Uri("http://127.0.0.1:9010"))
	)

	def uploadClient(cpAuthToken: String) = new CpUploadClient(uploadConfBase.copy(cpauthToken = cpAuthToken))

	private def sparqlUri = new java.net.URI(s"${uploadConfBase.metaBase}/sparql")
	private val http = Http()

	def updateDto(dto: DataObjectDto): DataObjectDto = {
		val l3 = dto.specificInfo.left.getOrElse(???)
		val l3updated = l3.copy(
			spatial = Right(new URI("http://meta.icos-cp.eu/resources/latlonboxes/globalLatLonBox")),
			variables = Some(Seq("emission")),
		)
		dto.copy(
			submitterId = "CP",
			specificInfo = Left(l3updated)
		)
	}

	def updateEmissInventoriesMeta(): Unit = {
		val token = Source.fromFile(new java.io.File("/home/oleg/token.txt")).getLines().mkString
		val client = uploadClient(token)
		val sparql = new SparqlHelper(sparqlUri)

		sparql.emissionInventories
			.flatMap{envs =>
				executeSequentially(envs){uri =>
					client.getUploadDto[DataObjectDto](uri).flatMap{dto =>
						client.uploadSingleMeta(updateDto(dto)).andThen{
							case Success(_) => println(s"Uploaded $uri")
						}
					}
				}
			}
			.onComplete{
				case Success(Done) => println("Done!")
				case Failure(exception) => exception.printStackTrace()
			}
	}

	def reingestLatestSpatialNetcdfs(): Unit = {
		val token = Source.fromFile(new java.io.File("/home/oleg/token.txt")).getLines().mkString
		val client = uploadClient(token)
		val sparql = new SparqlHelper(sparqlUri)

		sparql.latestSpatialNetcdfs
			.flatMap{uris =>
				executeSequentially(uris){uri =>
					Future
						.fromTry(Sha256Sum.fromBase64Url(uri.getPath.split("/").last))
						.flatMap{hash =>
							println("Re-ingesting " + hash.id)
							client.reIngestObject(hash)
						}
				}
			}
			.onComplete{
				case Success(Done) => println("Done!")
				case Failure(exception) => exception.printStackTrace()
			}
	}
}
