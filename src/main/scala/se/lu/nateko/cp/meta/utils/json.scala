package se.lu.nateko.cp.meta.utils.json

import spray.json.JsObject
import spray.json.JsValue

def merge(objs: JsObject*): JsObject = JsObject(objs.flatMap(_.fields)*)

extension (jsObj: JsObject)
	def ++(other: JsObject): JsObject = merge(jsObj, other)
	def +(field: (String, JsValue)): JsObject = JsObject(jsObj.fields + field)
	def ++(fields: Iterable[(String, JsValue)]): JsObject = JsObject(jsObj.fields ++ fields)
