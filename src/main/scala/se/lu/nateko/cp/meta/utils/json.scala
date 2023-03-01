package se.lu.nateko.cp.meta.utils.json

import spray.json.JsObject

def merge(objs: JsObject*): JsObject = JsObject(objs.flatMap(_.fields)*)

extension (jsObj: JsObject)
	def ++(other: JsObject): JsObject = merge(jsObj, other)
