package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Resource}
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.api.{RdfLenses, SparqlRunner}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{PlainStaticItem, StaticCollection}
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated

import scala.util.Using

/** Uses the original collection reader, prefetching only its potentially large member list. */
private[linkeddata] final class CollectionLandingPageBuilder(
	repo: Repository,
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	objectReader: StaticObjectReader
):
	import CollectionLandingPageBuilder.BatchSize

	private val server = new Rdf4jInstanceServer(repo)

	def staticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		server.access: conn ?=>
			val collectionIri = vocab.getCollection(hash)
			val members = prefetchMembers(conn, collectionIri)
			for
				collLens <- lenses.collectionLens
				docLens <- lenses.documentLens
				collection <- objectReader.fetchStaticColl(collectionIri, Some(hash), members)(
					using collLens, docLens
				)
			yield collection

	private def prefetchMembers(
		conn: SparqlRunner,
		collectionIri: IRI
	)(using Envri): Validated[Seq[PlainStaticItem]] =
		val snapshot = SailRepository(MemoryStore())
		snapshot.init()
		try
			Using.resource(snapshot.getConnection()): target =>
				val found = Using.resource(conn.evaluateTupleQuery(memberQuery(collectionIri))): rows =>
					rows.map: bindings =>
						val member = bindings.getValue("member").asInstanceOf[IRI]
						target.add(
							collectionIri,
							metaVocab.dcterms.hasPart,
							member,
							bindings.getValue("context").asInstanceOf[Resource]
						)
						member
					.toIndexedSeq.distinct

				found.grouped(BatchSize).foreach: batch =>
					Using.resource(conn.evaluateTupleQuery(memberMetadataQuery(batch))): rows =>
						rows.foreach: bindings =>
							target.add(
								bindings.getValue("subject").asInstanceOf[Resource],
								bindings.getValue("predicate").asInstanceOf[IRI],
								bindings.getValue("object"),
								bindings.getValue("context").asInstanceOf[Resource]
							)
				found

			Rdf4jInstanceServer(snapshot).access: snapshotConn ?=>
				for
					collLens <- lenses.collectionLens
					docLens <- lenses.documentLens
					result <- objectReader.fetchCollectionMembers(collectionIri)(using collLens, docLens)
				yield result
		finally snapshot.shutDown()

	private def memberQuery(collectionIri: IRI): String =
		s"""SELECT DISTINCT ?member ?context
			|WHERE {
			|  GRAPH ?context {
			|    <${collectionIri.stringValue}> <${metaVocab.dcterms.hasPart.stringValue}> ?member
			|  }
			|  FILTER(isIRI(?member))
			|}""".stripMargin

	private def memberMetadataQuery(members: Seq[IRI]): String =
		val memberValues = members.iterator.map(sparqlIri).mkString(" ")
		val predicates = Seq(RDF.TYPE, metaVocab.hasSha256sum, metaVocab.hasName, metaVocab.dcterms.title)
			.iterator.map(sparqlIri).mkString(" ")
		s"""SELECT DISTINCT ?subject ?predicate ?object ?context
			|WHERE {
			|  VALUES ?subject { $memberValues }
			|  VALUES ?predicate { $predicates }
			|  GRAPH ?context { ?subject ?predicate ?object }
			|}""".stripMargin

	private def sparqlIri(iri: IRI): String = s"<${iri.stringValue}>"

private object CollectionLandingPageBuilder:
	private val BatchSize = 250
