package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Literal, Statement}
import org.eclipse.rdf4j.model.impl.{LinkedHashModel, SimpleValueFactory}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.eclipse.rdf4j.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

object NTriplesFileExporter:

	val ChunkSize = 10000
	private val vf = SimpleValueFactory.getInstance()
	private val XsdBoolean = "http://www.w3.org/2001/XMLSchema#boolean"
	private val XsdInteger = "http://www.w3.org/2001/XMLSchema#integer"
	private val XsdDateTimeIri = vf.createIRI("http://www.w3.org/2001/XMLSchema#dateTime")

	def writeNTriples(
		updates: CloseableIterator[RdfUpdate],
		outputFile: Path,
		writeContext: IRI
	): Try[Long] =

		val graphUri = writeContext.stringValue

		Using(updates): updates =>
			val repo = new SailRepository(new MemoryStore)
			repo.init()

			try
				var updatesProcessed = 0
				Using.resource(repo.getConnection): conn =>
					for update <- updates do
						if update.isAssertion then
							conn.add(update.statement)
						else
							conn.remove(update.statement)
						updatesProcessed += 1
						if updatesProcessed % 100000 == 0 then
							println(s"NTriplesExport $graphUri: processed $updatesProcessed updates into in-memory store")

				println(s"NTriplesExport $graphUri: finished processing $updatesProcessed updates, writing to file")

				val tmpFile = outputFile.resolveSibling(outputFile.getFileName.toString + ".tmp")
				var totalWritten = 0L

				Using.resource(new BufferedOutputStream(new FileOutputStream(tmpFile.toFile))): out =>
					Using.resource(repo.getConnection): conn =>
						val allStatements = conn.getStatements(null, null, null)
						try
							val batch = new LinkedHashModel()
							var batchSize = 0
							for stmt <- allStatements.iterator.asScala do
								batch.add(normalizeStatement(stmt))
								batchSize += 1
								if batchSize >= ChunkSize then
									Rio.write(batch, out, RDFFormat.NTRIPLES)
									totalWritten += batchSize
									batch.clear()
									batchSize = 0
							if batchSize > 0 then
								Rio.write(batch, out, RDFFormat.NTRIPLES)
								totalWritten += batchSize
						finally
							allStatements.close()

				Files.move(tmpFile, outputFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
				println(s"NTriplesExport $graphUri: done, wrote $totalWritten triples to $outputFile")
				totalWritten
			finally
				repo.shutDown()

	private def looksLikeDateTime(label: String): Boolean =
		label.endsWith("Z") || label.matches("\\d{4}-\\d{2}-\\d{2}.*")

	private def normalizeStatement(stmt: Statement): Statement =
		stmt.getObject match
			case lit: Literal if lit.getDatatype != null =>
				val rawLabel = lit.getLabel
				val dtype = lit.getDatatype.stringValue
				val trimmed = rawLabel.trim
				val normalizedLabel = if dtype == XsdBoolean then trimmed.toLowerCase else trimmed
				val normalizedType =
					if dtype == XsdInteger && looksLikeDateTime(trimmed) then XsdDateTimeIri
					else lit.getDatatype
				if normalizedLabel != rawLabel || (normalizedType ne lit.getDatatype) then
					vf.createStatement(stmt.getSubject, stmt.getPredicate,
						vf.createLiteral(normalizedLabel, normalizedType))
				else stmt
			case _ => stmt

end NTriplesFileExporter
