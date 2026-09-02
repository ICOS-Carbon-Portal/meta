package se.lu.nateko.cp.meta.utils.geo

import org.locationtech.jts.geom.GeometryFactory

/**
 * Shared JTS `GeometryFactory` instance and tuning constant used both by the geo-spatial
 * query index (`rdfStore`) and the upload-side geo-coverage merger (`meta`). Must remain a
 * single shared instance/value: `GeometryFactory` carries the precision model and SRID, so
 * duplicating it per module would risk subtly different geometry semantics between the index
 * and the upload-side coverage merger.
 */
val JtsGeoFactory = new GeometryFactory()
val ConcaveHullLengthRatio = 0.8
