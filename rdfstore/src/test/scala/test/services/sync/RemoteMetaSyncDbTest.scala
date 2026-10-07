package se.lu.nateko.cp.meta.test.services.sync

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Statement}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.sync.{QueryThrottle, RemoteMetaSync, SyncKind, SyncProgress}
import se.lu.nateko.cp.meta.services.{CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDb
import se.lu.nateko.cp.meta.utils.rdf4j.{accessEagerly, transact}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.IteratorHasAsScala

@tags.DbTest
class RemoteMetaSyncDbTest extends AnyFunSpec with BeforeAndAfterAll:

	private given system: ActorSystem = ActorSystem("RemoteMetaSyncDbTest")
	private given EnvriConfigs = MetaCoreConfig.default.envriConfigs
	override def afterAll(): Unit = system.terminate()

	private lazy val db = TestDb()
	private lazy val remote = Rdf4jSparqlRunner(db.repo)

	private def statements(repo: Repository, subj: IRI | Null): Seq[Statement] =
		repo.accessEagerly(_.getStatements(subj, null, null, false).iterator.asScala.toIndexedSeq)

	private def sync(local: Repository): Seq[SyncProgress] =
		val progress = RemoteMetaSync(local, remote, prune = true, QueryThrottle.none).run(SyncKind.values.toSeq).runWith(Sink.seq)
		Await.result(progress, 2.minutes)

	describe("RemoteMetaSync against a meta SPARQL repository with the magic index"):

		it("finds nothing to change when synchronizing a repository with itself"):
			val finalProgress = sync(db.repo).groupMapReduce(_.kind)(identity)((_, last) => last)
			SyncKind.values.foreach: kind =>
				val progress = finalProgress(kind)
				assert(progress.roots > 0, kind)
				assert(progress.added == 0 && progress.removed == 0 && progress.missingRemotely == 0, progress)

		it("restores the indexed metadata of data objects from the remote, given only their types and the ontology"):
			val local = SailRepository(MemoryStore())
			local.init()
			val meta = CpmetaVocab(local.getValueFactory)
			val types = statements(db.repo, null).filter: st =>
				st.getPredicate == RDF.TYPE || st.getContext.stringValue == "http://meta.icos-cp.eu/ontologies/cpmeta/"
			local.transact(conn => types.foreach(conn.add(_))).get

			val progress = sync(local)
			assert(progress.exists(p => p.kind == SyncKind.DataObjects && p.added > 0))

			val objects = statements(db.repo, null).collect:
				case st if st.getPredicate == RDF.TYPE && st.getObject == meta.dataObjectClass => st.getSubject
			assert(objects.nonEmpty)
			val indexed = RemoteMetaSync.dataObjectPredicates.toSet
			objects.take(20).foreach:
				case obj: IRI =>
					val acqAndSubm = statements(db.repo, obj).collect:
						case st if st.getPredicate == meta.wasAcquiredBy || st.getPredicate == meta.wasSubmittedBy => st.getObject
					(obj +: acqAndSubm).foreach:
						case subj: IRI =>
							val expected = statements(db.repo, subj)
								.filter(st => st.getContext != null && indexed.contains(st.getPredicate.stringValue)).toSet
							assert(expected.nonEmpty, subj)
							assert(statements(local, subj).filter(_.getContext != null).toSet == expected, subj)
						case _ =>
				case _ =>
