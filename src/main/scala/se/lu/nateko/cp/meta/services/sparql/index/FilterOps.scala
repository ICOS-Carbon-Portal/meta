package se.lu.nateko.cp.meta.services.sparql.index

import HierarchicalBitmap._

final class FilterOps(val self: Filter) extends AnyVal{

	def optimize: Filter = flatten.mergeIntervals.removeRedundantReqProps

	def flatten: Filter = self match{
		case And(filters) =>
			val subfilters = filters.flatMap(_.flatten match {
				case And(filters) => filters
				case All => Nil
				case other => Iterable(other)
			})
			if(subfilters.isEmpty) All
			else if(subfilters.contains(Nothing)) Nothing
			else And(subfilters)

		case Or(filters) =>
			val subfilters = filters.flatMap(_.flatten match {
				case Or(filters) => filters
				case other => Iterable(other)
			})
			if(subfilters.isEmpty) Nothing
			else if(subfilters.contains(All)) All
			else Or(subfilters)

		case other => other
	}

	def removeRedundantReqProps: Filter = {
		def extractContProps(f: Filter): Seq[ContProp] = f match{
			case ContFilter(prop, _) => Seq(prop)
			case And(filters) => filters.flatMap(extractContProps)
			case Or(filters) => filters.flatMap(extractContProps)
			case _ => Nil
		}
		replaceRecursively{
			case RequiredProps(props) => RequiredProps(
				props.distinct.diff(extractContProps(self))
			)
		}
	}

	def replaceRecursively(pf: PartialFunction[Filter, Filter]): Filter = pf.andThen{
		case And(filters) => And(filters.map(_.replaceRecursively(pf)))
		case Or(filters) => Or(filters.map(_.replaceRecursively(pf)))
		case other => other
	}(self)

	def existsRecursively(pred: Filter => Boolean): Boolean = pred(self) || (self match{
		case And(filters) => filters.exists(pred)
		case Or(filters) => filters.exists(pred)
		case _ => false
	})

	def mergeIntervals: Filter = self match{
		case And(filters) =>

			val (minMaxes, rest) = filters.partitionMap(_ match{
				case min @ ContFilter(_, MaxFilter(_, _)) => Left(min)
				case max @ ContFilter(_, MinFilter(_, _)) => Left(max)
				case other => Right(other.mergeIntervals)
			})

			if(minMaxes.isEmpty) And(rest) else{
				val collapsed = minMaxes.groupBy(_.property).flatMap{case (prop, propFilters) => {

					type VT = prop.ValueType

					def makeFilter(cond1: FilterRequest[VT], cond2: FilterRequest[VT]): Option[IntervalFilter[VT]] = (cond1, cond2) match{
						case (min: MinFilter[VT], max: MaxFilter[VT]) => Some(IntervalFilter(min, max))
						case (max: MaxFilter[VT], min: MinFilter[VT]) => Some(IntervalFilter(min, max))
						case _ => None
					}

					propFilters.toList match{
						case ContFilter(`prop`, cond1) :: ContFilter(`prop`, cond2) :: Nil =>
							makeFilter(cond1, cond2)
								.map{intFilt =>
									ContFilter(prop, intFilt)
								}
								.fold(propFilters)(Seq(_))
						case _ =>
							propFilters
					}

				}}
				And(collapsed.toSeq ++ rest)
			}
		case Or(filters) => Or(filters.map(_.mergeIntervals))
		case other => other
	}
}

