package se.lu.nateko.cp.meta.test.metaexport

import se.lu.nateko.cp.meta.core.data.*
import java.net.URI

object TestGeoFeatures:
	val geoFeatures = Vector(
					Position(48.476357,2.780096,Some(103.0f),Some("FR-Fon"),None),
					Circle(Position(48.475868,2.780064,None,None,None),25.2,None,None),
					Circle(Position(48.476095,2.77934,None,None,None),25.2,None,None),
					Circle(Position(48.476536,2.779682,None,None,None),25.2,None,None),
					Circle(Position(48.476225,2.780558,None,None,None),25.2,None,None),
					Polygon(Vector(Position(48.475908,2.785777,None,None,None), 
					Position(48.478101,2.787856,None,None,None),
					Position(48.479848,2.786875,None,None,None), 
					Position(48.480506,2.786456,None,None,None),
					Position(48.480789,2.785678,None,None,None),
					Position(48.480814,2.784824,None,None,None), 
					Position(48.480794,2.783953,None,None,None), 
					Position(48.480944,2.783584,None,None,None), 
					Position(48.480903,2.783067,None,None,None),
					Position(48.480689,2.782683,None,None,None),
					Position(48.477977,2.777622,None,None,None),
					Position(48.476735,2.775239,None,None,None),
					Position(48.47323,2.779432,None,None,None),
					Position(48.474982,2.782723,None,None,None),
					Position(48.473986,2.783961,None,None,None)),Some("TA"),None))
		
		val oceanGeoTracks = Vector(
					GeoTrack(
						Vector(
							Position(57.438,10.545,None,None,None),
							Position(57.49,11.364,None,None,None),
							Position(56.483,11.959,None,None,None),
							Position(56.036,12.654,None,None,None),
							Position(56.056,10.852,None,None,None),
							Position(56.7,11.913,None,None,None),
							Position(57.669,11.164,None,None,None),
							Position(57.826,9.413,None,None,None),
							Position(57.796,6.668,None,None,None),
							Position(58.388,5.308,None,None,None),
							Position(59.746,-1.746,None,None,None),
							Position(62.0,-6.766,None,None,None),
							Position(63.334,-21.366,None,None,None),
							Position(64.012,-22.995,None,None,None),
							Position(64.188,-22.047,None,None,None),
							Position(61.779,-35.959,None,None,None),
							Position(59.562,-43.881,None,None,None),
							Position(63.864,-52.267,None,None,None),
							Position(63.996,-52.275,None,None,None),
							Position(64.159,-51.726,None,None,None)
						),None,Some(URI("http://meta.icos-cp.eu/resources/spcov_w4oYzE_oBW15PfaVTTolOdqc"))),
					GeoTrack(
						Vector(
							Position(64.123,-51.889,None,None,None),
							Position(63.889,-52.277,None,None,None),
							Position(61.87,-50.167,None,None,None),
							Position(60.092,-48.277,None,None,None),
							Position(59.042,-45.139,None,None,None),
							Position(58.666,-42.009,None,None,None),
							Position(59.12,-40.092,None,None,None),
							Position(60.929,-34.162,None,None,None),
							Position(64.141,-22.857,None,None,None),
							Position(63.511,-22.08,None,None,None),
							Position(63.19,-19.948,None,None,None),
							Position(64.055,-15.074,None,None,None),
							Position(64.9,-13.521,None,None,None),
							Position(62.059,-7.709,None,None,None),
							Position(58.833,0.899,None,None,None),
							Position(57.714,6.822,None,None,None),
							Position(57.685,7.91,None,None,None),
							Position(56.77,11.866,None,None,None),
							Position(56.053,10.835,None,None,None),
							Position(56.158,10.252,None,None,None)
						),None,Some(URI("http://meta.icos-cp.eu/resources/spcov_GhWmNZuocNFf9_Kf8A2OGRrY"))),
					GeoTrack(
						Vector(
							Position(56.115,10.414,None,None,None),
							Position(56.184,12.347,None,None,None),
							Position(56.014,12.667,None,None,None),
							Position(56.201,12.161,None,None,None),
							Position(56.052,10.823,None,None,None),
							Position(56.698,11.91,None,None,None),
							Position(57.679,11.13,None,None,None),
							Position(57.839,10.268,None,None,None),
							Position(57.807,6.649,None,None,None),
							Position(58.712,4.488,None,None,None),
							Position(59.176,3.899,None,None,None),
							Position(60.901,-0.776,None,None,None),
							Position(62.0,-6.766,None,None,None),
							Position(63.317,-21.328,None,None,None),
							Position(64.141,-23.225,None,None,None),
							Position(62.967,-35.093,None,None,None),
							Position(59.102,-42.663,None,None,None),
							Position(59.791,-48.399,None,None,None),
							Position(64.002,-52.265,None,None,None),
							Position(64.167,-51.722,None,None,None)
						),None,Some(URI("http://meta.icos-cp.eu/resources/spcov_13JNLkPUbi75EpLhujK8CtQK"))))
