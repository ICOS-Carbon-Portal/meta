package se.lu.nateko.cp.meta.utils

import scala.reflect.ClassTag

/** Used only by rdfStore's SPARQL pattern matching (`FilterPatternSearch`, `DofPatternSearch`). */
extension (inner: AnyRef)
	def asOptInstanceOf[T: ClassTag]: Option[T] = inner match{
		case t: T => Some(t)
		case _ => None
	}
