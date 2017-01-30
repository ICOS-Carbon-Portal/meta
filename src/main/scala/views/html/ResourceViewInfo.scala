package views.html

import se.lu.nateko.cp.meta.core.data.UriResource
import ResourceViewInfo.PropValue

case class ResourceViewInfo(
	res: UriResource,
	comment: Option[String],
	types: List[UriResource],
	propValues: List[(UriResource, PropValue)],
	usage: Seq[(UriResource, UriResource)]
)

object ResourceViewInfo{
	type PropValue = Either[UriResource, String]
}