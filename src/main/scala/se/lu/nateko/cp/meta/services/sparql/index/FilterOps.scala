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
			else if(subfilters.size == 1) subfilters.head
			else And(subfilters)

		case Or(filters) =>
			val subfilters = filters.flatMap(_.flatten match {
				case Or(filters) => filters
				case Nothing => Nil
				case other => Iterable(other)
			})
			if(subfilters.isEmpty) Nothing
			else if(subfilters.contains(All)) All
			else if(subfilters.size == 1) subfilters.head
			else Or(subfilters)

		case RequiredProps(props) if props.isEmpty => All

		case other => other
	}

	def removeRedundantReqProps: Filter = replace{
		case RequiredProps(props) => RequiredProps(
			props.distinct.diff(self.propsRequiredByConfFilters.toSeq)
		)
	}

	def propsRequiredByConfFilters: Set[ContProp] = self match{
		case And(subs) => subs.map(_.propsRequiredByConfFilters).reduce(_ union _)
		case ContFilter(prop, _) => Set(prop)
		case Or(subs) => subs.map(_.propsRequiredByConfFilters).reduce(_ intersect _)
		case _ => Set.empty
	}

	def replace(pf: PartialFunction[Filter, Filter]): Filter = pf.orElse[Filter, Filter]{
		case And(filters) => And(filters.map(_.replace(pf)))
		case Or(filters) => Or(filters.map(_.replace(pf)))
		case other => other
	}(self)

	def exists(pred: Filter => Boolean): Boolean = pred(self) || (self match{
		case And(filters) => filters.exists(_.exists(pred))
		case Or(filters) => filters.exists(_.exists(pred))
		case _ => false
	})

	def exists(pf: PartialFunction[Filter, Unit]): Boolean = exists(pf.isDefinedAt _)

	def collect[T](pf: PartialFunction[Filter, T]): Seq[T] = collectS(self, pf.andThen(Seq(_)))

	private def collectS[T](f: Filter, pf: PartialFunction[Filter, Seq[T]]): Seq[T] = pf
		.applyOrElse[Filter, Seq[T]](f, _ match{
			case And(filters) => filters.flatMap(collectS(_, pf))
			case Or(filters) => filters.flatMap(collectS(_, pf))
			case _ => Nil
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
