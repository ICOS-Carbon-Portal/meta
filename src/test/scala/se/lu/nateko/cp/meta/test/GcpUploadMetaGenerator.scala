package se.lu.nateko.cp.meta.test

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

import scala.io.Source

import org.eclipse.rdf4j.sail.memory.model.MemValueFactory

import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j.toJava
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.ValueFactory

/**
 * One-off code to produce upload metadata packages for GCP dataset publication
 */
object GcpUploadMetaGenerator extends CpmetaJsonProtocol {

	val folder = "/home/maintenance/Documents/CP/L3metadata/GCPupload/"

	def updatePackageWithContributors(inFile: String, outFile: String): Unit = {
		given factory: ValueFactory = new MemValueFactory
		import TestConfig.envriConfs
		val vocab = new CpVocab(factory)
		given Envri = Envri.ICOS

		val contribs = Source.fromFile(folder + "gcpPeople.txt").getLines().map{line =>
			val Seq(lname, fname) = line.split(", ").toSeq
			vocab.getPerson(fname, lname).toJava
		}.toIndexedSeq

		val inJsonText = Source.fromFile(folder + inFile).getLines().mkString("\n")
		import spray.json.*

		val oldMeta = inJsonText.parseJson.convertTo[DataObjectDto]

		val newMeta = oldMeta.copy(
			specificInfo = oldMeta.specificInfo.left.map(
				epm => epm.copy(
					production = epm.production.copy(contributors = contribs)
				)
			)
		)

		val newJsonStr = newMeta.toJson.prettyPrint

		val os = new FileOutputStream(new File(folder + outFile))
		os.write(newJsonStr.getBytes(Charset.forName("UTF-8")))
		os.close()
	}
}
