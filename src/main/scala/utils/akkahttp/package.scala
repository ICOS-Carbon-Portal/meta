package se.lu.nateko.cp.meta.utils.akkahttp

import akka.Done
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCodes}
import akka.stream.Materializer
import se.lu.nateko.cp.meta.utils.async.{error, ok}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

def responseToDone(errCtxt: String)(resp: HttpResponse)(implicit ctxt: ExecutionContext, mat: Materializer): Future[Done] =
	if(resp.status.isSuccess) {
		resp.discardEntityBytes()
		ok
	} else errorFromResp(resp, errCtxt)


def errorFromResp[T](resp: HttpResponse, errCtxt: String)(implicit ctxt: ExecutionContext, mat: Materializer): Future[T] = resp.entity.toStrict(2.seconds)
	.transform{
		case Success(entity) => Success(s"$errCtxt :\n" + entity.data.utf8String)
		case _ => Success(errCtxt)
	}.flatMap{msg =>
		error(s"Got ${resp.status} from the server: $msg")
	}

def parseIfOk[T](errCtxt: String)(parser: ResponseEntity => Future[T])(resp: HttpResponse)(implicit ctxt: ExecutionContext, mat: Materializer): Future[T] = resp.status match{
	case StatusCodes.OK => parser(resp.entity)
	case _ => errorFromResp(resp, errCtxt)
}
