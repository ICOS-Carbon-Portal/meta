package se.lu.nateko.cp.meta.ingestion

import scala.io.Source
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.metaflow.Researcher
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.concurrent.Future
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import scala.util.Using
import se.lu.nateko.cp.meta.api.CloseableIterator
import scala.concurrent.ExecutionContext

class PeopleAndOrgsIngester(pathToTextRes: String)(implicit envriConfs: EnvriConfigs, exe: ExecutionContext) extends Ingester{

	override def isAppendOnly = true

	private val ingosRegexp = """^(.+),\ (.+):\ (.+)\ \((.+)\)$""".r
	private val gcpRegexp = """^(.+),\ (.+)$""".r
	private val fluxnetRegexp = """^(.+),(.+),(.+)$""".r
	private case class OrgInfo(orgName: String, orgId: UriId)
	private case class Info(lname: String, fname: String, email: Option[String], org: Option[OrgInfo])

	def getStatements(factory: ValueFactory): Ingestion.Statements = Future{

		implicit val f = factory
		implicit val envri = Envri.ICOS
		val vocab = new CpVocab(factory)
		val metaVocab = new CpmetaVocab(factory)
		val role = Researcher

		val info = Using(
			Source.fromInputStream(getClass.getResourceAsStream(pathToTextRes), "UTF-8")
		){
			_.getLines().map(_.trim).filter(!_.isEmpty).map{
				case ingosRegexp(lname, fname, orgName, orgId) =>
					Info(lname, fname, None, Some(OrgInfo(orgName, UriId(orgId))))
				case gcpRegexp(lname, fname) =>
					Info(lname, fname, None, None)
				case fluxnetRegexp(lname, fname, email) =>
					Info(lname, fname, Some(email), None)

			}.toIndexedSeq
		}.get

		val orgTriples = info.collect{
			case Info(_, _, _, Some(orgInfo)) => orgInfo
		}.distinct.flatMap{
			case OrgInfo(orgName, orgId) =>
				val org = vocab.getOrganization(orgId)
				Seq[(IRI, IRI, Value)](
					(org, RDF.TYPE, metaVocab.orgClass),
					(org, metaVocab.hasName, orgName.toRdf),
					(org, RDFS.LABEL, orgId.urlSafeString.toRdf)
				)
		}

		val personTriples = info
			.distinctBy(p => (p.lname, p.fname))
			.flatMap{p =>
				val person = vocab.getPerson(p.fname, p.lname)
				Seq[(IRI, IRI, Value)](
					(person, RDF.TYPE, metaVocab.personClass),
					(person, metaVocab.hasFirstName, p.fname.toRdf),
					(person, metaVocab.hasLastName, p.lname.toRdf)
				) ++ p.email.map{mail =>
					(person, metaVocab.hasEmail, mail.toRdf)
				}
			}

		val membershipTriples = info.collect{
			case Info(lname, fname, _, Some(OrgInfo(_, orgId))) =>
				val org = vocab.getOrganization(orgId)
				val person = vocab.getPerson(fname, lname)
				val membership = vocab.getMembership(orgId, role, lname)
				Seq[(IRI, IRI, Value)](
					(person, metaVocab.hasMembership, membership),
					(membership, RDF.TYPE, metaVocab.membershipClass),
					(membership, RDFS.LABEL, s"$lname as ${role.name} at $orgId".toRdf),
					(membership, metaVocab.hasRole, vocab.getRole(role)),
					(membership, metaVocab.atOrganization, org)
				)
		}.flatten
		val iter = (orgTriples ++ personTriples ++ membershipTriples).map(factory.tripleToStatement).iterator
		new CloseableIterator.Wrap(iter, () => ())
	}
}
