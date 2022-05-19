package se.lu.nateko.cp.meta.services.sparql.index


object StringHierarchicalBitmap{
	import HierarchicalBitmap.*

	val SpilloverThreshold = 513

	given Ord: Ordering[String] with{

		def compare(x: String, y: String): Int = {
			val lx = x.length; val ly = y.length
			val lmin = Math.min(lx, ly)
			var i = 0
			while(i < lmin){
				val sx = x.charAt(i).toShort
				val sy = y.charAt(i).toShort
				if(sx < sy) return -1
				if(sx > sy) return 1
				i += 1
			}
			if(lx == ly) 0
			else if (lx < ly) -1
			else 1
		}
	}


	def apply(stringLookup: Int => String): HierarchicalBitmap[String] = {

		given Geo[String] with{

			val spilloverThreshold: Int = SpilloverThreshold

			def keyLookup(value: Int): String = stringLookup(value)

			def coordinate(key: String, depth: Int): Coord =
				if(depth < 1) 0
				else if(depth > key.length) Short.MinValue
				else key.charAt(depth - 1).toShort
		}

		new HierarchicalBitmap[String](0, None)
	}

}
