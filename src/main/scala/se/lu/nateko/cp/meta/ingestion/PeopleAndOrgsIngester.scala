package se.lu.nateko.cp.meta.ingestion

import scala.io.Source
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.icos.Researcher
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs

class PeopleAndOrgsIngester(pathToTextRes: String)(implicit envriConfs: EnvriConfigs) extends Ingester{

	override def isAppendOnly = true

	private val ingosRegexp = """^(.+),\ (.+):\ (.+)\ \((.+)\)$""".r
	private val gcpRegexp = """^(.+),\ (.+)$""".r
	private case class OrgInfo(orgName: String, orgId: UriId)
	private case class Info(lname: String, fname: String, org: Option[OrgInfo])

	def getStatements(factory: ValueFactory): Ingestion.Statements = {

		implicit val f = factory
		implicit val envri = Envri.ICOS
		val vocab = new CpVocab(factory)
		val metaVocab = new CpmetaVocab(factory)
		val roleId = UriId(Researcher.name)
		val role = vocab.getRole(roleId)

		val info = Source
			.fromInputStream(getClass.getResourceAsStream(pathToTextRes), "UTF-8")
			.getLines.map(_.trim).filter(!_.isEmpty).map{
				case ingosRegexp(lname, fname, orgName, orgId) =>
					Info(lname, fname, Some(OrgInfo(orgName, UriId(orgId))))
				case gcpRegexp(lname, fname) =>
					Info(lname, fname, None)
			}.toIndexedSeq

		val orgTriples = info.collect{
			case Info(_, _, Some(orgInfo)) => orgInfo
		}.distinct.flatMap{
			case OrgInfo(orgName, orgId) =>
				val org = vocab.getOrganization(orgId)
				Seq[(IRI, IRI, Value)](
					(org, RDF.TYPE, metaVocab.orgClass),
					(org, metaVocab.hasName, orgName.toRdf),
					(org, RDFS.LABEL, orgId.urlSafeString.toRdf)
				)
		}

		val personTriples = info.collect{
			case Info(lname, fname, _) => (lname, fname)
		}.distinct.flatMap{
			case (lname, fname) =>
				val person = vocab.getPerson(fname, lname)
				Seq[(IRI, IRI, Value)](
					(person, RDF.TYPE, metaVocab.personClass),
					(person, metaVocab.hasFirstName, fname.toRdf),
					(person, metaVocab.hasLastName, lname.toRdf)
				)
		}

		val membershipTriples = info.collect{
			case Info(lname, fname, Some(OrgInfo(_, orgId))) =>
				val org = vocab.getOrganization(orgId)
				val person = vocab.getPerson(fname, lname)
				val membership = vocab.getMembership(orgId, roleId, lname)
				Seq[(IRI, IRI, Value)](
					(person, metaVocab.hasMembership, membership),
					(membership, RDF.TYPE, metaVocab.membershipClass),
					(membership, RDFS.LABEL, s"$lname as $roleId at $orgId".toRdf),
					(membership, metaVocab.hasRole, role),
					(membership, metaVocab.atOrganization, org)
				)
		}.flatten
		Future.successful((orgTriples ++ personTriples ++ membershipTriples).map(factory.tripleToStatement).iterator)
	}
}
