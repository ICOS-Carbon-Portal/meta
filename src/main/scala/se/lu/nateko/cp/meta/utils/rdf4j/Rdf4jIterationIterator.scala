package se.lu.nateko.cp.meta.utils.rdf4j

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import se.lu.nateko.cp.meta.api.CloseableIterator

import scala.collection.AbstractIterator

class Rdf4jIterationIterator[T](res: CloseableIteration[T], closer: () => Unit = () => ()) extends CloseableIterator.Wrap[T](
	res.asPlainScalaIterator,
	() =>
		try
			res.close()
		finally
			closer()
)
