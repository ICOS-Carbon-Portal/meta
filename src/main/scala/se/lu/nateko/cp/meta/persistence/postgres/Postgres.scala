package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.{Connection, DriverManager}
import scala.util.Try

final case class DbCredentials(db: String, user: String, password: String)
final case class DbServer(host: String, port: Int)

object Postgres {

	private lazy val driverClass = Class.forName("org.postgresql.Driver")

	def getConnection(serv: DbServer, creds: DbCredentials): Try[Connection] = Try{
		driverClass
		val url = s"jdbc:postgresql://${serv.host}:${serv.port}/${creds.db}"
		DriverManager.getConnection(url, creds.user, creds.password)
	}
}