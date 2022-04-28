package se.lu.nateko.cp.meta.services.sparql.index

import HierarchicalBitmap.*

extension (self: Filter){

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

		case other => other
	}

	def removeRedundantReqProps: Filter = {
		val alreadyRequired = propsRequiredByFilters
		replace{
			case Exists(prop) if alreadyRequired.contains(prop) => All
		}
	}

	def propsRequiredByFilters: Set[Property] = self match{
		case And(subs) => subs.map(_.propsRequiredByFilters).reduce(_ union _)
		case ContFilter(prop, _) => Set(prop)
		case CategFilter(prop, _) => Set(prop)
		case Or(subs) => subs.map(_.propsRequiredByFilters).reduce(_ intersect _)
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

					val PropValFilterReq = ContFilter.FilterExtractor(prop)

					propFilters.toList match{
						case PropValFilterReq(cond1) :: PropValFilterReq(cond2) :: Nil =>
							makeIntervalFilter(cond1, cond2)
								.map{intFilt =>
									ContFilter(PropValFilterReq.property, intFilt)
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

private def makeIntervalFilter[T](cond1: FilterRequest[T], cond2: FilterRequest[T]): Option[IntervalFilter[T]] = (cond1, cond2) match{
	case (min: MinFilter[T], max: MaxFilter[T]) => Some(IntervalFilter(min, max))
	case (max: MaxFilter[T], min: MinFilter[T]) => Some(IntervalFilter(min, max))
	case _ => None
}
