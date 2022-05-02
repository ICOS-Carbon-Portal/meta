package se.lu.nateko.cp.meta.test.api

import scala.language.postfixOps

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{PidEntry, PidUpdate, EpicPidClient}
import spray.json.JsString
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.compiletime.uninitialized


class EpicPidClientTest extends AnyFunSpec with BeforeAndAfterAll{

	private var system: ActorSystem = uninitialized
	given ActorSystem = system
	var ep: EpicPidClient = uninitialized

	override def beforeAll(): Unit = {
		system = ActorSystem("EpicPidTestSystem")
		ep = EpicPidClient.default
	}

	override def afterAll(): Unit = {
		system.terminate()
	}

	val suffix = "UNIT-TEST"
	var randomSuffix = "not set yet"
	val waitTime = 10
	val originalUrl = "http://www.icos-cp.eu/unit-test"
	val originalEmail = "unit-test@test.net"
	val editedUrl = "http://www.icos-cp.eu/unit-test-edited"
	val editedEmail = "unit-test-edited@test.net"

	def testPid(url: String, email: String) = {
		Seq(
			PidUpdate(
				`type` = "URL",
				parsed_data = JsString(url)
			),
			PidUpdate(
				`type` = "EMAIL",
				parsed_data = JsString(email)
			)
		)
	}

	def jsValueToString(jsVal: PidEntry): String = {
		jsVal.parsed_data match{
			case JsString(s) => s
			case _ => "WRONG"
		}
	}

	ignore("EpicPid operations"){

		it("creates a new PID with specified name"){

			val r = ep.createNew(suffix, testPid(originalUrl, originalEmail))

			Await.result(r, waitTime second)

		}

		it("recreates the newly created PID"){

			val r = ep.createOrRecreate(suffix, testPid(originalUrl, originalEmail))

			Await.result(r, waitTime second)

		}

		it("creates a PID with random name") {

			val r = ep.createRandom(testPid(originalUrl, originalEmail))

			randomSuffix = Await.result(r, waitTime second)

			assert(randomSuffix.length > 30)

		}

		it("lists existing pids"){

			val l = ep.list

			val pids = Await.result(l, waitTime second)

			assert(pids.contains(suffix))
			assert(pids.contains(randomSuffix.toUpperCase))

		}

		it("lists data for specific PID"){

			val p = ep.get(randomSuffix)

			val pid = Await.result(p, waitTime second)

			assert(jsValueToString(pid(0)) === originalUrl)
			assert(jsValueToString(pid(1)) === originalEmail)

		}

		it("allows us to edit existing PID"){

			val r = ep.update(suffix, testPid(editedUrl, editedEmail))

			Await.result(r, waitTime second)

			val p = ep.get(suffix)

			val pid = Await.result(p, waitTime second)

			assert(jsValueToString(pid(0)) === editedUrl)
			assert(jsValueToString(pid(1)) === editedEmail)

		}

		it("allows us to delete specific PID (fixed suffix)"){

			val r = ep.delete(suffix)

			Await.result(r, waitTime second)

		}

		it("allows us to delete specific PID (random suffix)"){

			val r = ep.delete(randomSuffix)

			Await.result(r, waitTime second)

		}
	}
}
