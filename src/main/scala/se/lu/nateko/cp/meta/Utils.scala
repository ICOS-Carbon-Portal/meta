package se.lu.nateko.cp.meta

import com.google.common.base.Optional

object Utils {

	implicit class GoogleScalaOptionable[T](val opt: Optional[T]) extends AnyVal{
		def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None
	}
}