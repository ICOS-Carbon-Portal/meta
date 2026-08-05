package se.lu.nateko.cp.meta.persistence.postgres

// Split out of rdfstore/src/main/scala/persistence/postgres/Postgres.scala (task 14):
// CpmetaConfig (now in rdfCommon) needs these two case classes as plain config value
// carriers, but the JDBC connection logic in `Postgres` stays in rdfStore, which is the
// only module with the postgres driver on its classpath.
case class DbCredentials(db: String, user: String, password: String)
case class DbServer(host: String, port: Int)
