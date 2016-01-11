package se.lu.nateko.cp.meta.api.test

import akka.actor.ActorSystem
import scala.util.{Success, Failure}
import org.scalatest.{BeforeAndAfterAll, FunSpec}
import se.lu.nateko.cp.meta.api.{PidExisting, PidUpdate, EpicPid}
import spray.json.{JsValue, JsString}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class EpicPidTest extends FunSpec with BeforeAndAfterAll{

	implicit val system = ActorSystem("EpicPidTestSystem")

	override def afterAll(){
		system.shutdown()
	}

	val pidName = "UNIT-TEST"
	val waitTime = 15
	val originalUrl = "http://www.icos-cp.eu/unit-test"
	val originalEmail = "unit-test@test.net"
	val editedUrl = "http://www.icos-cp.eu/unit-test-edited"
	val editedEmail = "unit-test@test.net-edited"

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

	def jsValueToString(jsVal: PidExisting): String = {
		jsVal.parsed_data match{
			case JsString(s) => s
			case _ => "WRONG"
		}
	}

	describe("EpicPid operations"){

//		it("creates a new PID with specified name"){
//
//			val ep = EpicPid.default
//			val r = ep.createPidWithName(pidName, testPid(originalUrl, originalEmail))
//
//			val result = Await.result(r, waitTime second)
//
//			assert(result === ())
//		}
//
//		it("lists existing pids"){
//
//			val ep = EpicPid.default
//			val l = ep.listPids()
//
//			val pids = Await.result(l, waitTime second)
//
//			assert(pids.contains(pidName))
//
//		}
//
//		it("lists data for specific PID"){
//
//			val ep = EpicPid.default
//			val p = ep.getPid(pidName)
//
//			val pid = Await.result(p, waitTime second)
//
//			assert(jsValueToString(pid(0)) === originalUrl)
//			assert(jsValueToString(pid(1)) === originalEmail)
//
//		}
//
//		it("allows us to edit existing PID"){
//
//			val ep = EpicPid.default
//			val r = ep.editPid(pidName, testPid(editedUrl, editedEmail))
//
//			Await.result(r, waitTime second)
//
//			val p = ep.getPid(pidName)
//
//			val pid = Await.result(p, waitTime second)
//
//			assert(jsValueToString(pid(0)) === editedUrl)
//			assert(jsValueToString(pid(1)) === editedEmail)
//
//		}

		it("allows us to delete specific PID"){

			val ep = EpicPid.default
			val r = ep.deletePid("sdfg")

			val result = Await.result(r, waitTime second)

			println(result)

//			r.onComplete{
//				case Success(s: Unit) => println("S")
//				case Failure(t) => println(t.getMessage)
//			}

		}
	}
}
