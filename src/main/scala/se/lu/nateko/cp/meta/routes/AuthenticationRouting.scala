package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.cpauth.core.Authenticator
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.cpauth.core.CookieToToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.StandardRoute
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive0
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import scala.util.Success
import scala.util.Failure
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Rejection
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.headers.`X-Forwarded-For`
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.server.util.ClassMagnet

class AuthenticationRouting(authConfig: PublicAuthConfig) extends CpmetaJsonProtocol{
	import AuthenticationRouting._

	private[this] val authenticator = Authenticator(authConfig).get

	private def user(inner: UserInfo => Route): Route = cookie(authConfig.authCookieName)(cookie => {
		val userTry = for(
			token <- CookieToToken.recoverToken(cookie.value);
			uinfo <- authenticator.unwrapUserInfo(token)
		) yield uinfo

		userTry match {
			case Success(uinfo) => inner(uinfo)
			case Failure(err) => reject(InvalidCpauthTokenRejection(toMessage(err)))
		}
	})

	def mustBeLoggedIn(inner: UserInfo => Route): Route = handleRejections(authRejectionHandler)(user(inner))

	private def toMessage(err: Throwable): String = {
		val msg = err.getMessage
		if(msg == null || msg.isEmpty) err.getClass.getName else msg
	}
	
	def allowUsers(userIds: Seq[String])(inner: => Route): Route =
		if(userIds.isEmpty) inner else {
			mustBeLoggedIn{ uinfo =>
				if(userIds.contains(uinfo.mail.toLowerCase))
					inner
				else
					forbid(s"User ${uinfo.givenName} ${uinfo.surname} is not authorized to perform this operation")
			}
		}

	def route(implicit mat: Materializer) = get{
		path("whoami"){
			mustBeLoggedIn{uinfo => complete(uinfo)}
		}
	}

	//TODO Make the cpauth login url and the name of the query param configurable
	def ensureLogin(inner: => Route): Route = user(uinfo => inner) ~
		extractUri{uri =>
			redirect(Uri("https://cpauth.icos-cp.eu/login/").withQuery(Query("targetUrl" -> uri.toString)), StatusCodes.Found)
		}
}

object AuthenticationRouting {

	def forbid(msg: String): StandardRoute = complete((StatusCodes.Forbidden, msg))

	case class InvalidCpauthTokenRejection(message: String) extends Rejection

	val authRejectionHandler = RejectionHandler.newBuilder().handle{
			case InvalidCpauthTokenRejection(message) =>
				forbid(message)
		}.result

	val ensureLocalRequest: Directive0 =
		optionalHeaderValueByType[`X-Forwarded-For`](())
			.require(_.isEmpty)
			.recover(_ => complete(StatusCodes.Forbidden))
}
