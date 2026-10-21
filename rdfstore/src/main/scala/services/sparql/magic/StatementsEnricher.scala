package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.{CloseableIteration, EmptyIteration, SingletonIteration, UnionIteration}
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.repository.sparql.federation.CollectionIteration
import org.eclipse.rdf4j.sail.SailException
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.services.derived.{DerivedMetadata, DerivedMetadataService}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.{createStringLiteral, toRdf}

import java.util.Arrays
import scala.collection.immutable.SeqMap

class StatementsEnricher(derived: DerivedMetadataService, metaVocab: CpmetaVocab) {
	import StatementsEnricher.StatIter
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
		var derivedCache: Option[Option[DerivedMetadata]] = None
		def resolved: Option[DerivedMetadata] = derivedCache.getOrElse:
			val value = subj match
				case iri: IRI => derived.resolve(iri).metadata
				case _ => None
			derivedCache = Some(value)
			value
		SeqMap(
			metaVocab.hasBiblioInfo -> (() => {
				import spray.json.*
				import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
				val refs = resolved.map(_.references)
				refs.map(js => factory.createStringLiteral(js.toJson.compactPrint))
			}),
			metaVocab.hasCitationString -> (
				() => resolved.flatMap(_.citationString).map(factory.createStringLiteral)
			),
			metaVocab.dcterms.license -> (
				() => resolved.flatMap(_.licence).map(_.url.toRdf)
			)
		)
	}
}

object StatementsEnricher{
	type StatIter = CloseableIteration[? <: Statement]
}
