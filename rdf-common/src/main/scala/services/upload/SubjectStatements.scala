package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.{IRI, Value}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, StatementSource}

/** An explicit, short-lived property snapshot for parsing one metadata resource. */
private[upload] object SubjectStatements:
	def apply(subject: IRI)(using source: StatementSource): StatementSource =
		val result = source.getStatements(subject, null, null)
		val statements = try result.toIndexedSeq finally result.close()
		new StatementSource:
			private def matching(s: IRI | Null, p: IRI | Null, o: Value | Null): Iterator[RdfStatement] =
				require(s == subject, "A subject snapshot only supports reads of its own resource")
				statements.iterator.filter(st => (p == null || st.predicate == p) && (o == null || st.obj == o))

			def getStatements(s: IRI | Null, p: IRI | Null, o: Value | Null): CloseableIterator[RdfStatement] =
				new CloseableIterator.Wrap(matching(s, p, o), () => ())

			def hasStatement(s: IRI | Null, p: IRI | Null, o: Value | Null): Boolean = matching(s, p, o).hasNext
