package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.Connection
import java.sql.DriverManager

import scala.util.Try

case class DbCredentials(db: String, user: String, password: String)

object Postgres {

	private lazy val driverClass = Class.forName("org.postgresql.Driver")

	def getConnection(creds: DbCredentials): Try[Connection] = Try{
		driverClass
		val url = s"jdbc:postgresql:${creds.db}"
		DriverManager.getConnection(url, creds.user, creds.password)
	}
}