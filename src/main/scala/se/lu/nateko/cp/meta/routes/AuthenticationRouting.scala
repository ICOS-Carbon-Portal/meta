package se.lu.nateko.cp.meta.routes

import scala.language.unsafeNulls

import akka.http.javadsl.server.MissingCookieRejection
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{HttpCookie, SameSite, `X-Forwarded-For`}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Directive0, Directive1, Rejection, RejectionHandler, Route, StandardRoute}
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.cpauth.core.{Authenticator, CookieToToken, PublicAuthConfig, UserId}
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, EnvriResolver}
import spray.json.{JsNull, JsObject}

import scala.language.implicitConversions
import scala.util.{Failure, Success}

class AuthenticationRouting(authConf: Map[Envri, PublicAuthConfig])(using EnvriConfigs) extends CpmetaJsonProtocol{
	import AuthenticationRouting.*

	private val extractEnvri = extractEnvriDirective
	private def authConfig(using envri: Envri) = authConf(envri)
	private def authenticator(using Envri) = Authenticator(authConfig).get

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
				if(userIds.exists(uid.email.equalsIgnoreCase))
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

	def forbid(msg: String): Route = extractRequestContext: ctxt =>
		ctxt.request.discardEntityBytes(ctxt.materializer)
		complete(StatusCodes.Forbidden -> msg)

	case class InvalidCpauthTokenRejection(message: String) extends Rejection

	val authRejectionHandler = RejectionHandler.newBuilder().handle{
			case InvalidCpauthTokenRejection(message) => forbid(message)
			case cookieRej: MissingCookieRejection => forbid(
				s"Authentication cookie ${cookieRej.cookieName} not present. Login required for this operation."
			)
		}.result()

	val optEnsureLocalRequest: Directive0 =
		optionalHeaderValueByType(`X-Forwarded-For`)
			.require(_.isEmpty)

	val ensureLocalRequest: Directive0 = optEnsureLocalRequest
		.recover(_ => complete(StatusCodes.Forbidden -> "This is a private API method"))

	def extractEnvriOptDirective(using EnvriConfigs): Directive1[Envri] = extractHost.flatMap{h =>
		EnvriResolver.infer(h) match{
			case None => reject
			case Some(envri) => provide(envri)
		}
	}

	def extractEnvriDirective(using EnvriConfigs): Directive1[Envri] = extractHost.flatMap{h =>
		EnvriResolver.infer(h) match{
			case None => complete(StatusCodes.BadRequest -> s"Unexpected host $h, cannot find corresponding ENVRI")
			case Some(envri) => provide(envri)
		}
	}

}
