package se.lu.nateko.cp.meta.core.data

import spray.json.*
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.util.control.NoStackTrace

object GeoJson {

	class FormatException(msg: String) extends IllegalArgumentException(msg) with NoStackTrace

	def fromFeatureWithLabels(f: GeoFeature): JsObject = toGeometryOrFeatureWithLabels(f).fold(identity, identity)
	def fromFeature(f: GeoFeature): JsObject = toGeometryOrFeature(f).fold(identity, identity)

	//Right means Geometry or GeometryCollection was enough (i.e. no labels or Circles)
	//Left means Feature or FeatureCollection was necessary (Circle or label present)
	private def toGeometryOrFeatureWithLabels(f: GeoFeature): Either[JsObject, JsObject] =
		toGeometryOrFeature(f).fold(Left(_),
			geo => f.label match{
				case Some(_) => Left(wrapGeoInFeature(geo, f.label))
				case None => Right(geo)
			}
		)

	//Right means Geometry or GeometryCollection was enough (i.e. no Circles and no labels inside colls)
	//Left means Feature or FeatureCollection was necessary (Circle, or coll with Circle, or coll with label inside)
	private def toGeometryOrFeature(f: GeoFeature): Either[JsObject, JsObject] = f match{

		case GeoTrack(points, _, _) => Right(JsObject(
			"type"        -> JsString("LineString"),
			"coordinates" -> JsArray(points.map(coordinates).toVector)
		))

		case p: Position => Right(JsObject(
			"type"        -> JsString("Point"),
			"coordinates" -> coordinates(p)
		))

		case box: LatLonBox => toGeometryOrFeature(box.asPolygon)

		case Polygon(vertices, _, _) => Right(JsObject(
			"type"        -> JsString("Polygon"),
			"coordinates" -> JsArray(
				JsArray(shortCircuit(vertices.map(coordinates)).toVector)
			)
		))

		case Circle(center, radius, labelOpt, _) => Left(JsObject(
			"type"       -> JsString("Feature"),
			"geometry"   -> fromFeature(center),
			"properties" -> JsObject(
				Map("radius" -> JsNumber(Math.round(radius*100).toFloat / 100)) ++ labelOpt.map(
					lbl => "label" -> JsString(lbl)
				)
			)
		))

		case Pin(pos, kind) => Left(JsObject(
			"type"        -> JsString("Feature"),
			"geometry"    -> fromFeature(pos),
			"properties" -> JsObject(
				Map("pinkind" -> JsString(kind.toString)) ++ f.label.map(
					lbl => "label" -> JsString(lbl)
				)
			)
		))

		case FeatureCollection(features, _, _) =>
			val geomsOrFeats = features.map(toGeometryOrFeatureWithLabels).toVector

			val geomsOnly = geomsOrFeats.flatMap(_.toOption)

			if(geomsOnly.size == geomsOrFeats.size)
				Right(JsObject(
					"type"       -> JsString("GeometryCollection"),
					"geometries" -> JsArray(geomsOnly)
				))
			else{
				val featuresJs: Vector[JsObject] = geomsOrFeats.zip(features).map{
					case (geoOrFeat, feat) => geoOrFeat.fold(
						identity,
						geom => wrapGeoInFeature(geom, feat.label)
					)
				}
				Left(JsObject(
					"type"     -> JsString("FeatureCollection"),
					"features" -> JsArray(featuresJs)
				))
			}
		case FeatureWithGeoJson(feature, _) => toGeometryOrFeature(feature)
	}

	private def shortCircuit(vertices: Seq[JsArray]): Seq[JsArray] =
		if vertices.size < 3 then vertices else
			if vertices.head.elements == vertices.last.elements then vertices
			else vertices :+ vertices.head

	private def wrapGeoInFeature(geo: JsObject, labelOpt: Option[String]) = JsObject(
		"type" -> JsString("Feature"),
		"geometry" -> geo,
		"properties" -> labelOpt.fold(JsObject.empty)(lbl => JsObject("label" -> JsString(lbl)))
	)

	def toFeature(geoJs: String): Try[GeoFeature] =
		Try(geoJs.parseJson.asJsObject).flatMap(toFeature)

	def toFeature(json: JsObject): Try[GeoFeature] = {

		def field(name: String): Try[JsValue] = json.fields.get(name).fold[Try[JsValue]](
				fail(s"'$name' not found in ${json.compactPrint}")
			)(Success.apply)

		def coords = field("coordinates")

		def featuresColl(fieldName: String): Try[FeatureCollection] = field(fieldName).collect{
			case JsArray(elements) => FeatureCollection(
				elements.map{
					case o: JsObject => toFeature(o).get
					case other =>
						throw new FormatException(s"Expected JsObject, got ${other.compactPrint}")
				},
				None,
				None
			)
			case other =>
				throw new FormatException(s"Expected '$fieldName' to be a JsArray, got ${other.compactPrint}")
		}

		field("type").collect{ case JsString(geoType) => geoType }.flatMap{

			case "Point" => coords.flatMap(parsePosition)

			case "LineString" => coords.flatMap(parsePointsArray).map(GeoTrack(_, None, None))

			case "Polygon" => coords.map{
				case JsArray(Vector(pntArr)) => {
					val points = parsePointsArray(pntArr).get
					if(points.size < 2) throw new FormatException(s"Expected polygon, got ${points.size} points: ${pntArr.compactPrint}")
					else Polygon(points.dropRight(1), None, None)
				}
				case other =>
					throw new FormatException(s"Expected polygon coordinates to be a single-element JsArray, got ${other.compactPrint}")
			}

			case "GeometryCollection" => featuresColl("geometries")
			case "FeatureCollection" => featuresColl("features")

			case "Feature" => for(
				geoJs <- field("geometry");
				geo   <- toFeature(geoJs.asJsObject);
				props <- field("properties")
			) yield {
				val lblOpt = props match{
					case o: JsObject => o.fields.get("label").collect{case JsString(lbl) => lbl}
					case _ => None
				}
				(geo, props) match{
					case (p: Position, prop: JsObject) if prop.fields.contains("radius") =>
						val radius = prop.fields.get("radius").collect{case JsNumber(value) => value.floatValue}.getOrElse{
							throw new FormatException("Expected numeric 'radius' property in " + prop.prettyPrint)
						}
						Circle(p, radius, lblOpt, None)
					case (p: Position, prop: JsObject) if prop.fields.contains("pinkind") =>
						val pinkind = prop.fields.get("pinkind")
							.collect{
								case JsString(value) =>
									try PinKind.valueOf(value)
									catch case _ => throw FormatException(s"Bad Pin kind: $value")
							}.getOrElse{
								throw FormatException("Expected 'pinkind' property in " + prop.prettyPrint)
							}
						Pin(p, pinkind)
					case _ =>
						geo.withOptLabel(lblOpt)
				}
			}

			case other => fail(s"Unsupported GeoJSON object type: $other")
		}
	}

	private def parsePointsArray(geoJson: JsValue): Try[Vector[Position]] = geoJson match{
		case JsArray(elements) => Try{
			elements.map(p => parsePosition(p).get)
		}
		case _ =>
			fail(s"Expected JSON array, got ${geoJson.compactPrint}")
	}

	private def parsePosition(geoJson: JsValue): Try[Position] = geoJson match {
		case JsArray(Vector(JsNumber(lon), JsNumber(lat))) =>
			Success(Position.ofLatLon(lat.doubleValue, lon.doubleValue))
		case JsArray(Vector(JsNumber(lon), JsNumber(lat), JsNumber(elev))) =>
			Success(Position(lat.doubleValue, lon.doubleValue, Some(elev.floatValue), None, None))
		case _ =>
			fail(s"Not a valid JSON for GeoJSON for a position: ${geoJson.compactPrint}")
	}

	private def coordinates(p: Position) = {
		val latLon = Vector(JsNumber(p.lon6.toDouble), JsNumber(p.lat6.toDouble))
		val coords = p.alt.fold(latLon){alt => latLon :+ JsNumber(alt)}
		JsArray(coords)
	}

	private def fail(msg: String) = Failure(new FormatException(msg))
}
