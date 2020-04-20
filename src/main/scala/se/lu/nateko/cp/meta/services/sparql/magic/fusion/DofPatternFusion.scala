package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index._
import se.lu.nateko.cp.meta.utils.rdf4j._

import DofPatternFusion._

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.query.algebra.Exists
import org.eclipse.rdf4j.query.algebra.Not
import org.eclipse.rdf4j.query.algebra.StatementPattern
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.ValueExpr

case class FusionResult(
	fetch: DataObjectFetch,
	exprsToFuse: Set[TupleExpr],
	propVars: VarPropLookup,
	isPureCpIndexQuery: Boolean
)

class DofPatternFusion(meta: CpmetaVocab){

	def findFusions(patt: DofPattern): Seq[FusionResult] = patt match{
		case DofPattern.Empty => Nil

		case pdp @ ProjectionDofPattern(_, _, _, Some(outer)) =>
			findFusions(pdp.copy(outer = None)) ++ findFusions(outer)

		case pdp: ProjectionDofPattern => findFusions(pdp.inner) match{
			case Seq(singleResult) => Seq(addOrderByAndOffset(pdp, singleResult))
			case any => any
		}

		case list: DofPatternList => list.subs.flatMap(findFusions)

		case union: DofPatternUnion =>
			val subs = union.subs.flatMap(findFusions)

			val isValid = subs.forall(dof => dof.fetch.sort.isEmpty && dof.fetch.offset == 0)

			if(isValid){//should always be true in practice, here for extra code robustness and clarity
				unionVarProps(subs.map(_.propVars)).map{propVals =>
					val filter = Or(subs.map(_.fetch.filter))
					val exprs = subs.map(_.exprsToFuse).reduce(_ union _)
					val isPure = subs.forall(_.isPureCpIndexQuery)
					FusionResult(DataObjectFetch(filter, None, 0), exprs, propVals, isPure)
				}.toSeq
			} else
				Nil

		case plain: PlainDofPattern => findPlainFusion(plain).toSeq

	}

	def addOrderByAndOffset(pdp: ProjectionDofPattern, inner: FusionResult): FusionResult = if(!inner.isPureCpIndexQuery) inner else{
		val sortBy = pdp.orderBy.map(op => op -> inner.propVars.get(op.sortVar)).collect{
			case (op, Some(cp: ContProp)) => SortBy(cp, op.descending)
		}
		val exprs = inner.exprsToFuse ++ sortBy.flatMap(_ => pdp.orderBy.map(_.expr)) ++ pdp.offset.map(_.slice)
		inner.copy(
			fetch = inner.fetch.copy(sort = sortBy, offset = pdp.offset.fold(0)(_.offset)),
			exprsToFuse = exprs
		)
	}

	def findPlainFusion(patt: PlainDofPattern): Option[FusionResult] = patt.dobjVar.collect{
		//if dobj is pre-specified, then there is no need for SPARQL magic
		case dobjVar if(patt.varValues.get(dobjVar).flatMap(_.vals).isEmpty) =>

			val varProps = getVarPropLookup(patt)

			val andOrFilterParser = new FilterPatternSearch(v => varProps.get(v).collect{case cp: ContProp => cp})

			val filtsAndExprs = patt.filters.flatMap{fexp =>
				parseFilter(fexp, varProps)
					.orElse(andOrFilterParser.parseFilterExpr(fexp))
					.map(_ -> fexp.getParentNode)
			}
			val filts = filtsAndExprs.map(_._1)
			val filtExprs = filtsAndExprs.collect{case (_, te: TupleExpr) => te}

			val categFiltsAndExprs = for(
				(vname, valInfo) <- patt.varValues.toSeq;
				prop <- varProps.get(vname).collect{case cp: CategProp => cp}
			) yield{
				val vals = valInfo.vals.fold[Seq[IRI]](Nil)(s => s.collect{case iri: IRI => iri}.toSeq)
				prop match{
					case uriProp: UriProperty => CategFilter(uriProp, vals) -> valInfo.providers.toSet
					case optUri: OptUriProperty => CategFilter(optUri, vals.map(Some(_))) -> valInfo.providers.toSet
				}
			}

			val categFilts: Seq[Filter] = categFiltsAndExprs.map(_._1)
			val categExprs = categFiltsAndExprs.flatMap(_._2)
			val allFilts = And(categFilts ++ filts)

			val allExprs = filtExprs ++ categExprs ++ varProps.keys.flatMap(patt.propPaths.get).flatten.collect{
				//filenames are not in the index, need to leave this pattern in the query
				case StatementPattern2(pred, sp) if pred != meta.hasName => sp
			}

			val allRecognized = patt.propPaths.flatMap(_._2).forall{sp2 =>
				val objVar = sp2.sp.getObjectVar
				varProps.contains(objVar.getName) || (objVar.isAnonymous && !objVar.hasValue)
			}

			FusionResult(DataObjectFetch(allFilts, None, 0), allExprs.toSet, varProps, allRecognized)
	}

	def getVarPropLookup(patt: PlainDofPattern): VarPropLookup = {

		def endVarName(steps: IRI*): Option[String] = steps.reverse.toList match{
			case Nil => patt.dobjVar
			case head :: tail => for(
				prev <- endVarName(tail:_*);
				statPatts <- patt.propPaths.get(prev);
				statPat <- statPatts.find(_.pred === head)
			) yield statPat.targetVar
		}

		def propVar(prop: Property, steps: IRI*) = endVarName(steps:_*).map(_ -> prop)

		Seq(
			propVar(DobjUri),
			propVar(Spec           , meta.hasObjectSpec ),
			propVar(FileName       , meta.hasName       ),
			propVar(FileSize       , meta.hasSizeInBytes),
			propVar(Submitter      , meta.wasSubmittedBy , meta.prov.wasAssociatedWith),
			propVar(SubmissionStart, meta.wasSubmittedBy , meta.prov.startedAtTime    ),
			propVar(SubmissionEnd  , meta.wasSubmittedBy , meta.prov.endedAtTime      ),
			propVar(Station        , meta.wasAcquiredBy  , meta.prov.wasAssociatedWith),
			propVar(Site           , meta.wasAcquiredBy  , meta.wasPerformedAt        ),
			propVar(DataStart      , meta.wasAcquiredBy  , meta.prov.startedAtTime    ),
			propVar(DataEnd        , meta.wasAcquiredBy  , meta.prov.endedAtTime      ),
			propVar(SamplingHeight , meta.wasAcquiredBy  , meta.hasSamplingHeight     ),
		).flatten.toMap
	}

	def parseFilter(expr: ValueExpr, parProps: VarPropLookup): Option[Filter] = expr match{
		case not: Not => not.getArg match{
			case exists: Exists => exists.getSubQuery match{
				case sp: StatementPattern =>
					val (s, p, o) = splitTriple(sp)
					if(meta.isNextVersionOf == p.getValue && s.isAnonymous && !s.hasValue && !o.isAnonymous && parProps.get(o.getName) == Some(DobjUri))
						Some(FilterDeprecated)
					else
						None
				case _ => None
			}
			case _ => None
		}
		case _ => None
	}

	def fuse(fusions: Seq[FusionResult]): Unit = {
		//replace the deepest exprs that are not inside a union beloning to the exprs to be replaced
		???
	}
}

object DofPatternFusion{
	type PlainFusionRes = (Filter, Set[TupleExpr])
	type VarPropLookup = Map[String, Property]

	def unionVarProps(varProps: Seq[VarPropLookup]): Option[VarPropLookup] = varProps match{
		case Nil => Some(Map.empty[String, Property])
		case Seq(single) => Some(single)
		case Seq(vp1, rest @ _*) => unionVarProps(rest).flatMap{vp2 =>
			val keys = vp1.keySet.intersect(vp2.keySet)
			if(keys.forall(v => vp1(v) eq vp2(v))) Some(
				vp1.filter{
					case (v, _) => keys.contains(v)
				}
			) else None
		}
	}
}
