package se.lu.nateko.cp.meta.services.sparql.magic.stats

import scala.collection.JavaConverters._

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunction

import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.Filtering
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.SubmissionEnd

class StatsTupleFunction(indexThunk: () => CpIndex) extends TupleFunction{

	import StatsTupleFunction._

	override val getURI: String = hasStatProps

	override def evaluate(valueFactory: ValueFactory, args: Value*):
			CloseableIteration[_ <: java.util.List[_ <: Value], QueryEvaluationException] = {

		val defaultFiltering = new Filtering(Nil, true, Seq(SubmissionEnd))

		val iter = indexThunk().statEntries(defaultFiltering).iterator.map(entry =>

			args.map(_.stringValue match{

				case `sameAs` =>
					valueFactory.createBNode("statEntry_" + entry.hashCode)

				case `hasStatCount` =>
					valueFactory.createLiteral(entry.count.toString, XMLSchema.INTEGER)

				case `hasStatSubmitter` =>
					entry.key.submitter

				case `hasStatSpec` =>
					entry.key.spec

				case `hasStatStation` =>
					entry.key.station.getOrElse(null)

				case unknown => throw new QueryEvaluationException(
					s"Encountered an unsupported 'magic' predicate $unknown inside 'stats' block (ICOS CP plugin)"
				)
			}).asJava
		)

		new CloseableIteratorIteration(iter.asJava)
	}
}

object StatsTupleFunction{
	val hasStatProps = CpmetaVocab.MetaPrefix + "hasStatProps"
	val hasStatCount = CpmetaVocab.MetaPrefix + "hasStatCount"
	val hasStatSpec = CpmetaVocab.MetaPrefix + "hasStatSpec"
	val hasStatSubmitter = CpmetaVocab.MetaPrefix + "hasStatSubmitter"
	val hasStatStation = CpmetaVocab.MetaPrefix + "hasStatStation"
	val sameAs = OWL.SAMEAS.stringValue
}
