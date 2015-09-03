package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.cpauth.core.Authenticator
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.cpauth.core.CookieToToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.StandardRoute
import akka.http.scaladsl.server.Directives._
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import scala.util.Success
import scala.util.Failure
import akka.http.scaladsl.model.StatusCodes

class AuthenticationRouting(authConfig: PublicAuthConfig) {

	private[this] val authenticator = Authenticator(authConfig).get

	def user(inner: UserInfo => Route): Route = cookie(authConfig.authCookieName)(cookie => {
		val userTry = for(
			token <- CookieToToken.recoverToken(cookie.value);
			uinfo <- authenticator.unwrapUserInfo(token)
		) yield uinfo

		userTry match {
			case Success(uinfo) => inner(uinfo)
			case Failure(err) =>
				forbid(toMessage(err))
		}
	}) ~ forbid(s"Authentication cookie ${authConfig.authCookieName} was not set")

	private def forbid(msg: String): StandardRoute = complete((StatusCodes.Unauthorized, msg))

	private def toMessage(err: Throwable): String = {
		val msg = err.getMessage
		if(msg == null || msg.isEmpty) err.getClass.getName else msg
	}
	
	def allowUsers(userIds: Seq[String])(inner: => Route): Route = user{ uinfo =>
		if(userIds.isEmpty || userIds.contains(uinfo.mail))
			inner
		else
			forbid(s"User ${uinfo.givenName} ${uinfo.surname} is not authorized to perform this operation")
	}
}