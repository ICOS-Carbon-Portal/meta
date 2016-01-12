package se.lu.nateko.cp.meta.api.test

import akka.actor.ActorSystem
import scala.util.{Try, Success, Failure}
import org.scalatest.{BeforeAndAfterAll, FunSpec}
import se.lu.nateko.cp.meta.api.{PidEntry, PidUpdate, EpicPid}
import spray.json.{JsString}
import scala.concurrent.Await
import scala.concurrent.duration._


class EpicPidTest extends FunSpec with BeforeAndAfterAll{

	implicit val system = ActorSystem("EpicPidTestSystem")

	override def afterAll(){
		system.shutdown()
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

	def isSucess(value: Option[Try[Unit]]): Boolean = {
		value match {
			case Some(Success(s: Unit)) => true
			case Some(Failure(t)) => false
			case _ => false
		}
	}

	describe("EpicPid operations"){

		it("creates a new PID with specified name"){

			val ep = EpicPid.default
			val r = ep.create(suffix, testPid(originalUrl, originalEmail))

			val result = Await.ready(r, waitTime second)

			assert(isSucess(result.value) === true)

		}

		it("creates a PID with random name") {

			val ep = EpicPid.default
			val r = ep.create(testPid(originalUrl, originalEmail))

			randomSuffix = Await.result(r, waitTime second)

			assert(randomSuffix.length > 30)

		}

		it("lists existing pids"){

			val ep = EpicPid.default
			val l = ep.list

			val pids = Await.result(l, waitTime second)

			assert(pids.contains(suffix))
			assert(pids.contains(randomSuffix.toUpperCase))

		}

		it("lists data for specific PID"){

			val ep = EpicPid.default
			val p = ep.get(randomSuffix)

			val pid = Await.result(p, waitTime second)

			assert(jsValueToString(pid(0)) === originalUrl)
			assert(jsValueToString(pid(1)) === originalEmail)

		}

		it("allows us to edit existing PID"){

			val ep = EpicPid.default
			val r = ep.update(suffix, testPid(editedUrl, editedEmail))

			val result = Await.ready(r, waitTime second)

			assert(isSucess(result.value) === true)

			val p = ep.get(suffix)

			val pid = Await.result(p, waitTime second)

			assert(jsValueToString(pid(0)) === editedUrl)
			assert(jsValueToString(pid(1)) === editedEmail)

		}

		it("allows us to delete specific PID (fixed suffix)"){

			val ep = EpicPid.default
			val r = ep.delete(suffix)

			val result = Await.ready(r, waitTime second)

			assert(isSucess(result.value) === true)

		}

		it("allows us to delete specific PID (random suffix)"){

			val ep = EpicPid.default
			val r = ep.delete(randomSuffix)

			val result = Await.ready(r, waitTime second)

			assert(isSucess(result.value) === true)

		}
	}
}
