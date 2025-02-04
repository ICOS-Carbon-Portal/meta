package se.lu.nateko.cp.meta.test.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

class AlternativePathRouteTest extends AnyFunSpec with ScalatestRouteTest{

	val route = get{
		path("blabla.csv" | "blabla"){
			complete("yes")
		}
	}

	it("matches the path with file extension"){
		Get("/blabla.csv") ~> route ~> check{
			assert(status === StatusCodes.OK)
		}
	}

	it("matches the path without file extension"){
		Get("/blabla") ~> route ~> check{
			assert(status === StatusCodes.OK)
		}
	}
}
