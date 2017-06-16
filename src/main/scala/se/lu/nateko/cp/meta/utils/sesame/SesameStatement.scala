package se.lu.nateko.cp.meta.utils.sesame

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value

object SesameStatement {

	def unapply(st: Statement): Option[(IRI, IRI, Value)] = st.getSubject match{
		case subj: IRI => Some((subj, st.getPredicate, st.getObject))
		case _ => None
	}

}