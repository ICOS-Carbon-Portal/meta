package se.lu.nateko.cp.meta.api

import java.io.FileInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

import com.typesafe.sslconfig.akka.AkkaSSLConfig

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.UseHttp2
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import se.lu.nateko.cp.meta.HandleNetClientConfig
import spray.json._

class HandleNetClient(conf: HandleNetClientConfig)(implicit system: ActorSystem, mat: Materializer) extends DefaultJsonProtocol{
	import HandleNetClient.{ getCertificate, ok, readPrivateKey }
	import system.dispatcher
	private val http = Http()

	private val httpsCtxt = {

		val privKey = readPrivateKey(Paths.get(conf.clientPrivKeyPKCS8FilePath))
		val clientCert = getCertificate(conf.clientCertPemFilePath)
		val pkEntry = new KeyStore.PrivateKeyEntry(privKey, Array(clientCert))

		val keyStore = KeyStore.getInstance("JKS")
		keyStore.load(null, null)
		keyStore.setEntry("client", pkEntry, new KeyStore.PasswordProtection(Array.empty))

		val keyManFact = KeyManagerFactory.getInstance("PKIX")
		keyManFact.init(keyStore, Array.empty)

		val trustKeyStore = KeyStore.getInstance("JKS")
		trustKeyStore.load(null, null)
		val serverCert = getCertificate(conf.serverCertPemFilePath)
		trustKeyStore.setCertificateEntry("server", serverCert)

		val trustManFact = TrustManagerFactory.getInstance("PKIX")
		trustManFact.init(trustKeyStore)

		val rnd = SecureRandom.getInstance("NativePRNGNonBlocking")
		val sslCtxt = SSLContext.getInstance("TLSv1.2")

		sslCtxt.init(keyManFact.getKeyManagers, trustManFact.getTrustManagers, rnd)

		val d = http.defaultClientHttpsContext

		val akkaSslConf = if(conf.disableHostnameVerification) Some(
			AkkaSSLConfig(system).withSettings{
				val d = AkkaSSLConfig.defaultSSLConfigSettings(system)
				d.withLoose(d.loose.withDisableHostnameVerification(true))
			}
		) else d.sslConfig

		new HttpsConnectionContext(sslCtxt, akkaSslConf, d.enabledCipherSuites, d.enabledProtocols, d.clientAuth, d.sslParameters, UseHttp2.Negotiated)
	}

	private val authHeaders = RawHeader("Authorization", "Handle clientCert=\"true\"") :: Nil

	def list: Future[Seq[String]] = {
		val uriStr = s"${conf.baseUrl}api/handles?prefix=${conf.prefix}"

		http.singleRequest(
			HttpRequest(uri = Uri(uriStr), headers = authHeaders),
			httpsCtxt
		).flatMap(
			resp => resp.status match {
				case StatusCodes.OK =>
					Unmarshal(resp.entity).to[JsObject].map{resp =>
						resp.fields("handles").asInstanceOf[JsArray].elements.map{
							_.asInstanceOf[JsString].value
						}
					}
				case _ => errorFromResp(resp)
			}
		)
	}

	def get(suffix: String): Future[URL] = {
		http.singleRequest(
			HttpRequest(uri = Uri(pidUrlStr(suffix)), headers = authHeaders),
			httpsCtxt
		).flatMap(
			resp => resp.status match {
				case StatusCodes.OK =>
					Unmarshal(resp.entity).to[JsObject].map{resp =>
						val hdlVal = resp.fields("values").asInstanceOf[JsArray].elements.head.asJsObject
						val uriStr = hdlVal.fields("data").asJsObject.fields("value").asInstanceOf[JsString].value
						new URL(uriStr)
					}
				case _ => errorFromResp(resp)
			}
		)
	}

	def createOrRecreate(suffix: String, target: URL): Future[Done] = if(conf.dryRun) ok else {
		val payload = JsObject("values" -> JsArray(
			JsObject(
				"index" -> JsNumber(1),
				"type" -> JsString("URL"),
				"data" -> JsObject(
					"format" -> JsString("string"),
					"value" -> JsString(target.toString)
				)
			),
			JsObject(
				"index" -> JsNumber(100),
				"type" -> JsString("HS_ADMIN"),
				"data" -> JsObject(
					"format" -> JsString("admin"),
					"value" -> JsObject(
						"handle" -> JsString("0.NA/" + conf.prefix),
						"index" -> JsNumber(200),
						"permissions" -> JsString("011111110011")
					)
				)
			)
		))

		Marshal(payload).to[RequestEntity].flatMap{entity =>
			val req = HttpRequest(
				uri = Uri(pidUrlStr(suffix) + "?overwrite=true"),
				headers = authHeaders,
				method = HttpMethods.PUT,
				entity = entity
			)
			http.singleRequest(req, httpsCtxt).flatMap{resp =>
				resp.status match {
					case StatusCodes.OK | StatusCodes.Created =>
						resp.discardEntityBytes()
						ok
					case _ => errorFromResp(resp)
				}
			}
		}
	}

	def delete(suffix: String): Future[Done] = if(conf.dryRun) ok else {
		val req = HttpRequest(
			uri = Uri(pidUrlStr(suffix)),
			headers = authHeaders,
			method = HttpMethods.DELETE
		)
		http.singleRequest(req, httpsCtxt).flatMap{resp =>
			resp.status match {
				case StatusCodes.OK =>
					resp.discardEntityBytes()
					ok
				case _ => errorFromResp(resp)
			}
		}
	}

	private def errorFromResp[T](resp: HttpResponse): Future[T] = resp.toStrict(2.seconds)
		.transform{
			case Success(payload) => Success(":\n" + payload)
			case _ => Success("")
		}.flatMap{msg =>
			Future.failed(new Exception(s"Got ${resp.status} from the server$msg"))
		}

	private def pidUrlStr(suffix: String) = s"${conf.baseUrl}api/handles/${conf.prefix}/$suffix"

}

object HandleNetClient{

//	implicit val system = ActorSystem()
//	implicit val mat = ActorMaterializer()
//	import system.dispatcher
//
//	def stop = system.terminate()
//
//	def default = {
//		val conf = se.lu.nateko.cp.meta.ConfigLoader.default.dataUploadService.handle
//		new HandleNetClient(conf)
//	}

	def getCertificate(fileName: String) = {
		val certFact = CertificateFactory.getInstance("X.509")
		val certStr = new FileInputStream(fileName)
		val cert = certFact.generateCertificate(certStr)
		certStr.close()
		cert
	}

	def readPrivateKey(pkcs8DerFilePath: Path): RSAPrivateKey = {
		val spec = new PKCS8EncodedKeySpec(Files.readAllBytes(pkcs8DerFilePath))
		KeyFactory.getInstance("RSA").generatePrivate(spec).asInstanceOf[RSAPrivateKey]
	}

	def readPublicKey(x509DerFilePath: Path): RSAPublicKey = {
		val spec = new X509EncodedKeySpec(Files.readAllBytes(x509DerFilePath))
		KeyFactory.getInstance("RSA").generatePublic(spec).asInstanceOf[RSAPublicKey]
	}

	def getHandleNetKeyBytes(key: RSAPublicKey): Array[Byte] = {
		def sizeArr(size: Int): Array[Byte] = Array(24, 16, 8, 0).map(shift => (0xff & (size >> shift)).toByte)
		def withSize(arr: Array[Byte]): Seq[Array[Byte]] = Array(sizeArr(arr.size), arr)

		val arraySeq: Seq[Seq[Array[Byte]]] = Seq(
			withSize("RSA_PUB_KEY".getBytes("UTF8")),
			Seq(Array.fill(2)(0)), //2 bytes of flags, reserved for future use
			withSize(key.getPublicExponent.toByteArray),
			withSize(key.getModulus.toByteArray),
			Seq(Array.fill(4)(0)) //handle.net code makes a too large array (forgets that the flags are written without size)
		)
		Array.concat[Byte](arraySeq.flatten: _*)
	}

	def ok = Future.successful(Done)
}
