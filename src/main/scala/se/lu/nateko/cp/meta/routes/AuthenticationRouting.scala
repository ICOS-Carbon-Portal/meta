package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.cpauth.core.Authenticator
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.cpauth.core.CookieToToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.StandardRoute
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directive1
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig

import scala.util.Success
import scala.util.Failure
import akka.http.scaladsl.model.StatusCodes
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Rejection
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.headers.`X-Forwarded-For`
import spray.json.JsObject
import spray.json.JsNull
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.headers.SameSite

class AuthenticationRouting(authConf: Map[Envri, PublicAuthConfig])(implicit configs: EnvriConfigs) extends CpmetaJsonProtocol{
	import AuthenticationRouting._

	private val extractEnvri = extractEnvriDirective
	private def authConfig(implicit envri: Envri) = authConf(envri)
	private def authenticator(implicit envri: Envri) = Authenticator(authConfig).get

	private def user(inner: UserId => Route): Route = extractEnvri{implicit envri =>
		cookie(authConfig.authCookieName)(cookie => {
			val uidTry = for(
				signedToken <- CookieToToken.recoverToken(cookie.value);
				token <- authenticator.unwrapToken(signedToken)
			) yield token.userId

			uidTry match {
				case Success(uid) => inner(uid)
				case Failure(err) => reject(InvalidCpauthTokenRejection(toMessage(err)))
			}
		})
	}

	def mustBeLoggedIn(inner: UserId => Route): Route = handleRejections(authRejectionHandler)(user(inner))

	private def toMessage(err: Throwable): String = {
		val msg = err.getMessage
		if(msg == null || msg.isEmpty) err.getClass.getName else msg
	}
	
	def allowUsers(userIds: Seq[String])(inner: => Route): Route =
		if(userIds.isEmpty) inner else {
			mustBeLoggedIn{ uid =>
				if(userIds.contains(uid.email.toLowerCase))
					inner
				else
					forbid(s"User ${uid.email} is not authorized to perform this operation")
			}
		}

	def route = get{
		path("whoami"){
			user{uid => complete(uid)} ~
			complete(JsObject(Map("email" -> JsNull)))
		} ~
		(path("logout") & extractEnvri){implicit envri =>
			val cookie = HttpCookie(authConfig.authCookieName, "deleted")
				.withDomain(authConfig.authCookieDomain)
				.withSameSite(SameSite.Strict)
				.withMaxAge(1)
			setCookie(cookie){complete(StatusCodes.OK)}
		}
	}
}

object AuthenticationRouting {

	def forbid(msg: String): StandardRoute = complete((StatusCodes.Forbidden, msg))

	case class InvalidCpauthTokenRejection(message: String) extends Rejection

	val authRejectionHandler = RejectionHandler.newBuilder().handle{
			case InvalidCpauthTokenRejection(message) =>
				forbid(message)
		}.result()

	val ensureLocalRequest: Directive0 =
		optionalHeaderValueByType(`X-Forwarded-For`)
			.require(_.isEmpty)
			.recover(_ => complete(StatusCodes.Forbidden))


	def extractEnvriDirective(implicit configs: EnvriConfigs): Directive1[Envri] = extractHost.flatMap{h =>
		Envri.infer(h) match{
			case None => complete(StatusCodes.BadRequest -> s"Unexpected host $h, cannot find corresponding ENVRI")
			case Some(envri) => provide(envri)
		}
	}

}
