package se.lu.nateko.cp.meta.utils.rdf4j

import org.eclipse.rdf4j.model.{IRI, Statement, Value}

final case class Rdf4jStatement (
	subj: IRI,
	pred: IRI,
	obj: Value
)

object Rdf4jStatement {
	def unapply(st: Statement): Option[Rdf4jStatement] = st.getSubject match {
		case subj: IRI => Some(Rdf4jStatement(subj, st.getPredicate, st.getObject))
		case _ => None
	}
}
