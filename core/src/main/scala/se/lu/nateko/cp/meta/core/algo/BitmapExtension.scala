package se.lu.nateko.cp.meta.core.algo

import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.IntConsumer

object BitmapExtension {
	extension (bitmap: ImmutableRoaringBitmap)
		// Workaround because of -Yexplicit-nulls, which makes the existing overloads of
		// ImmutableRoaringBitmap.forEach fail when used with Scala lambdas.
		// In the end, the final result is an IntConsumer anyway when using those overloads,
		// so all we're doing here is making the type-system happy.
		def forEach(f: Int => Unit): Unit =
			bitmap.forEach(new IntConsumer {
				def accept(i: Int): Unit = f(i)
			})

}
