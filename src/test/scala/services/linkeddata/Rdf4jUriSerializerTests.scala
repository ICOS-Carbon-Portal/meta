package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec

class Rdf4jUriSerializerTests extends AnyFunSpec:

	describe("getViewInfo"):
		it("fetches outgoing properties and incoming usage with one SPARQL query"):
			val repo = SailRepository(MemoryStore())
			repo.init()
			try
				val factory = repo.getValueFactory
				val root = factory.createIRI("http://example.org/root")
				val target = factory.createIRI("http://example.org/target")
				val user = factory.createIRI("http://example.org/user")
				val pointsTo = factory.createIRI("http://example.org/pointsTo")
				val uses = factory.createIRI("http://example.org/uses")
				val conn = repo.getConnection
				try
					conn.add(root, RDFS.LABEL, factory.createLiteral("Root"))
					conn.add(root, pointsTo, target)
					conn.add(pointsTo, RDFS.LABEL, factory.createLiteral("points to"))
					conn.add(target, RDFS.LABEL, factory.createLiteral("Target"))
					conn.add(user, uses, root)
					conn.add(user, RDFS.LABEL, factory.createLiteral("User"))
					conn.add(uses, RDFS.LABEL, factory.createLiteral("uses"))
				finally conn.close()

				val info = Rdf4jUriSerializer.getViewInfo(Uri(root.stringValue), repo).get

				assert(info.res.label.contains("Root"))
				assert(info.propValues.exists: (property, value) =>
					property.uri.toString == pointsTo.stringValue &&
					value.left.exists(_.label.contains("Target"))
				)
				assert(info.usage.exists: (resource, property) =>
					resource.label.contains("User") && property.uri.toString == uses.stringValue
				)
			finally repo.shutDown()
