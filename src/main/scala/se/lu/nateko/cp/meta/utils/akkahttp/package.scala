package se.lu.nateko.cp.meta.utils

import akka.stream.Materializer
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.HttpResponse
import akka.Done
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success
import async.{ok, error}

package object akkahttp{

	def responseToDone(resp: HttpResponse)(implicit ctxt: ExecutionContext, mat: Materializer): Future[Done] =
		if(resp.status.isSuccess) {
			resp.discardEntityBytes()
			ok
		} else errorFromResp(resp)


	def errorFromResp[T](resp: HttpResponse)(implicit ctxt: ExecutionContext, mat: Materializer): Future[T] = resp.entity.toStrict(2.seconds)
		.transform{
			case Success(entity) => Success(":\n" + entity.data.utf8String)
			case _ => Success("")
		}.flatMap{msg =>
			error(s"Got ${resp.status} from the server$msg")
		}

}
