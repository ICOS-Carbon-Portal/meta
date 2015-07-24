package se.lu.nateko.cp.meta.utils.sesame

import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.Value

object SesameStatement {

	def unapply(st: Statement): Option[(URI, URI, Value)] = st.getSubject match{
		case subj: URI => Some((subj, st.getPredicate, st.getObject))
		case _ => None
	}

}