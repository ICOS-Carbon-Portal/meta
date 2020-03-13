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
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.utils.async._
import se.lu.nateko.cp.meta.utils.akkahttp._
import se.lu.nateko.cp.meta.core.data.Envri.Envri

class HandleNetClient(conf: HandleNetClientConfig)(implicit system: ActorSystem, mat: Materializer){
	import HandleNetClient._
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

		val d = http.defaultClientHttpsContext

		new HttpsConnectionContext(sslCtxt, d.sslConfig, d.enabledCipherSuites, d.enabledProtocols, d.clientAuth, d.sslParameters)
	}

	private val authHeaders = RawHeader("Authorization", "Handle clientCert=\"true\"") :: Nil

	def list(implicit envri: Envri): Future[Seq[String]] = {
		val uriStr = s"${conf.baseUrl}api/handles?prefix=${pidFactory.prefix}"

		http.singleRequest(
			HttpRequest(uri = Uri(uriStr), headers = authHeaders),
			httpsCtxt
		).flatMap(
			resp => resp.status match {
				case StatusCodes.OK =>
					Unmarshal(resp.entity).to[HandleList].map{_.handles}
				case _ => errorFromResp(resp)
			}
		)
	}

	def get(suffix: String)(implicit envri: Envri): Future[URL] = {
		http.singleRequest(
			HttpRequest(uri = Uri(pidFactory.pidUrlStr(suffix)), headers = authHeaders),
			httpsCtxt
		).flatMap(
			resp => resp.status match {
				case StatusCodes.OK =>
					Unmarshal(resp.entity).to[HandleValues].flatMap{resp =>
						resp.values.collectFirst{
							case UrlHandleValue(_, url) => Future.successful(url)
						}.getOrElse(
							error(s"Could not find URL value for $suffix")
						)
					}
				case _ => errorFromResp(resp)
			}
		)
	}

	def createOrRecreate(suffix: String, target: URL)(implicit envri: Envri): Future[Done] = if(conf.dryRun) ok else {
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
				headers = authHeaders,
				method = HttpMethods.PUT,
				entity = entity
			)
			http.singleRequest(req, httpsCtxt).flatMap(responseToDone)
		}
	}

	def delete(suffix: String)(implicit envri: Envri): Future[Done] = if(conf.dryRun) ok else {
		val req = HttpRequest(
			uri = Uri(pidFactory.pidUrlStr(suffix)),
			headers = authHeaders,
			method = HttpMethods.DELETE
		)
		http.singleRequest(req, httpsCtxt).flatMap(responseToDone)
	}

}

object HandleNetClient{

	class PidFactory(conf: HandleNetClientConfig){
		def prefix(implicit envri: Envri): String = conf.prefix.getOrElse(
			envri,
			throw new Exception(s"No PID prefix for ENVRI $envri in the config")
		)
		def getPid(suffix: String)(implicit envri: Envri) = s"${prefix}/$suffix"
		def getSuffix(hash: Sha256Sum): String = hash.id
		def getPid(hash: Sha256Sum)(implicit envri: Envri): String = getPid(getSuffix(hash))
		def pidUrlStr(suffix: String)(implicit envri: Envri) = s"${conf.baseUrl}api/handles/${getPid(suffix)}"
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
		Array.concat[Byte](arraySeq.flatten: _*)
	}
}
