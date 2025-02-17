package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.common.iteration.{CloseableIteration, EmptyIteration, SingletonIteration, UnionIteration}
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.repository.sparql.federation.CollectionIteration
import org.eclipse.rdf4j.sail.SailException
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.utils.rdf4j.{createStringLiteral, toRdf}

import java.util.Arrays
import scala.collection.immutable.SeqMap

class StatementsEnricher(val citer: CitationProvider) {
	import StatementsEnricher.StatIter
	import citer.metaVocab
	private given factory: ValueFactory = metaVocab.factory

	private def empty: StatIter = new EmptyIteration

	def enrich(base: StatIter, subj: Resource, pred: IRI, obj: Value): StatIter = {
		try
			val extras = getExtras(subj, pred, obj)
			if(!extras.hasNext) base else UnionIteration(base, extras)
		catch case err => 
			throw SailException(err.getMessage, err)
	}

	private def getExtras(subj: Resource, pred: IRI, obj: Value): StatIter = {
		if(subj == null || obj != null) empty //lookup by magic values/predicates not possible
		else{
			val magicFactories = magicPredValueFactories(subj)
			if(pred != null && !magicFactories.contains(pred)) empty //not a magic predicate
			else if(pred == null) {
				val extras = magicFactories.iterator.flatMap{
					(pred, thunk) => thunk().map(v => factory.createStatement(subj, pred, v))
				}
				new CollectionIteration(Arrays.asList(extras.toArray*))
			}
			else (
				for(thunk <- magicFactories.get(pred); v <- thunk()) yield
					new SingletonIteration(factory.createStatement(subj, pred, v))
			).getOrElse(empty)
		}
	}

	private def magicPredValueFactories(subj: Resource): Map[IRI, () => Option[Value]] = {
		var refsCache: Option[Option[References]] = None
		SeqMap(
			metaVocab.hasBiblioInfo -> (() => {
				import spray.json.*
				import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
				val refs = citer.getReferences(subj)
				refsCache = Some(refs)
				refs.map(js => factory.createStringLiteral(js.toJson.compactPrint))
			}),
			metaVocab.hasCitationString -> (
				() => refsCache.fold(citer.getCitation(subj))(_.flatMap(_.citationString)).map(factory.createStringLiteral)
			),
			metaVocab.dcterms.license -> (
				() => refsCache.fold(citer.getLicence(subj))(_.flatMap(_.licence)).map(_.url.toRdf)
			)
		)
	}
}

object StatementsEnricher{
	type StatIter = CloseableIteration[? <: Statement]
}
