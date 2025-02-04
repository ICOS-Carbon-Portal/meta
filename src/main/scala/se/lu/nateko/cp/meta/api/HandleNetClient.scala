package se.lu.nateko.cp.meta.api

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, RequestEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.HandleNetClientConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.utils.akkahttp.*
import se.lu.nateko.cp.meta.utils.async.*

import java.io.FileInputStream
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.security.cert.CertificateFactory
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class HandleNetClient(conf: HandleNetClientConfig)(using system: ActorSystem, mat: Materializer){
	import HandleNetClient.*
	import system.dispatcher

	private val http = Http()
	val pidFactory = new PidFactory(conf)
	private lazy val httpsCtxt = {

		val privKey = readPrivateKey(Paths.get(conf.clientPrivKeyPKCS8FilePath))
		val clientCert = getCertificate(conf.clientCertPemFilePath)
		val pkEntry = new KeyStore.PrivateKeyEntry(privKey, Array(clientCert))

		val keyStore = KeyStore.getInstance("JKS")
		keyStore.load(null, null)
		keyStore.setEntry("client", pkEntry, new KeyStore.PasswordProtection(Array.empty))

		val keyManFact = KeyManagerFactory.getInstance("PKIX")
		keyManFact.init(keyStore, Array.empty)


		val trustManFact = TrustManagerFactory.getInstance("PKIX")

		val trustKeyStoreOpt = conf.serverCertPemFilePath.map { serverCertPath =>
			val trustKeyStore = KeyStore.getInstance("JKS")
			trustKeyStore.load(null, null)
			val serverCert = getCertificate(serverCertPath)
			trustKeyStore.setCertificateEntry("server", serverCert)
			trustKeyStore
		}
		trustManFact.init(trustKeyStoreOpt.getOrElse(null))

		val rnd = SecureRandom.getInstance("NativePRNGNonBlocking")
		val sslCtxt = SSLContext.getInstance("TLSv1.2")

		sslCtxt.init(keyManFact.getKeyManagers, trustManFact.getTrustManagers, rnd)

		ConnectionContext.httpsClient(sslCtxt)
	}

	private val authHeader = RawHeader("Authorization", "Handle clientCert=\"true\"")

	def hdlRequest(req: HttpRequest): Future[HttpResponse] =
		val request = req.withHeaders(authHeader, req.headers*)
		timeLimit(http.singleRequest(request, httpsCtxt), 6.seconds, system.scheduler)

	def list(using Envri): Future[Seq[String]] = {
		val uriStr = s"${conf.baseUrl}api/handles?prefix=${pidFactory.prefix}"

		hdlRequest(HttpRequest(uri = Uri(uriStr)))
			.flatMap(parseIfOk("listing PIDs"){
				Unmarshal(_).to[HandleList].map(_.handles)
			})
	}

	def get(suffix: String)(using Envri): Future[URI] = {
		hdlRequest(
			HttpRequest(uri = Uri(pidFactory.pidUrlStr(suffix))),
		).flatMap(
			parseIfOk(s"Getting PID for $suffix"){
				Unmarshal(_).to[HandleValues].flatMap{hvs =>
					hvs.values.collectFirst{
						case UrlHandleValue(_, url) => Future.successful(url)
					}.getOrElse(
						error(s"Could not find URL value for $suffix")
					)
				}
			}
		)
	}

	def createOrRecreate(suffix: String, target: URI)(using Envri): Future[Done] = if(conf.dryRun) ok else {
		val payload = HandleValues(
			UrlHandleValue(1, target) ::
			AdminHandleValue(
				index = 100,
				admin = AdminValue(
					handle = "0.NA/" + pidFactory.prefix,
					index = 200,
					permissions = "011111110011"
				)
			) :: Nil
		)

		Marshal(payload).to[RequestEntity].flatMap{entity =>
			val req = HttpRequest(
				uri = Uri(pidFactory.pidUrlStr(suffix) + "?overwrite=true"),
				method = HttpMethods.PUT,
				entity = entity
			)
			hdlRequest(req).flatMap(responseToDone(s"Creating PID for $suffix"))
		}
	}

	def delete(suffix: String)(using Envri): Future[Done] = if(conf.dryRun) ok else {
		val req = HttpRequest(
			uri = Uri(pidFactory.pidUrlStr(suffix)),
			method = HttpMethods.DELETE
		)
		hdlRequest(req).flatMap(responseToDone(s"Deleting sufix $suffix"))
	}

}

object HandleNetClient{

	class PidFactory(conf: HandleNetClientConfig){
		def prefix(using envri: Envri): String = conf.prefix.getOrElse(
			envri,
			throw new Exception(s"No PID prefix for ENVRI $envri in the config")
		)
		def getPid(suffix: String)(using Envri) = s"${prefix}/$suffix"
		def getSuffix(hash: Sha256Sum): String = hash.id
		def getPid(hash: Sha256Sum)(using Envri): String = getPid(getSuffix(hash))
		def pidUrlStr(suffix: String)(using Envri) = s"${conf.baseUrl}api/handles/${getPid(suffix)}"
	}
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
		def withSize(arr: Array[Byte]): Seq[Array[Byte]] = Seq(sizeArr(arr.size), arr)

		val arraySeq: Seq[Seq[Array[Byte]]] = Seq(
			withSize("RSA_PUB_KEY".getBytes("UTF8")),
			Seq(Array.fill(2)(0)), //2 bytes of flags, reserved for future use
			withSize(key.getPublicExponent.toByteArray),
			withSize(key.getModulus.toByteArray),
			Seq(Array.fill(4)(0)) //handle.net code makes a too large array (forgets that the flags are written without size)
		)
		Array.concat[Byte](arraySeq.flatten*)
	}
}
