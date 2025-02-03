package se.lu.nateko.cp.meta.upload

import akka.Done
import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import se.lu.nateko.cp.meta.{CpmetaJsonProtocol, DataObjectDto}

import java.net.URI
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success}

object L3UpdateWorkbench extends CpmetaJsonProtocol{
	given system: ActorSystem = ActorSystem("l3update_workbench")
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

	def updateDto(dto: DataObjectDto): DataObjectDto = {
		val l3 = dto.specificInfo.left.getOrElse(???)
		val l3updated = l3.copy(
			spatial = new URI("http://meta.icos-cp.eu/resources/latlonboxes/globalLatLonBox"),
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
