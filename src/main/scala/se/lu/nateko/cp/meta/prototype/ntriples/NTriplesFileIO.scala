package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, StandardCopyOption}
import org.eclipse.rdf4j.rio.{Rio, RDFFormat}
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler
import org.eclipse.rdf4j.model.Statement
import scala.util.Using

object NTriplesFileIO {

	def load(file: File, store: NTriplesSailStore): Unit = {
		if (!file.exists()) return

		val parser = Rio.createParser(RDFFormat.TRIG)
		parser.setRDFHandler(new AbstractRDFHandler {
			override def handleStatement(st: Statement): Unit = {
				store.addStatement(st, explicit = true)
			}
			override def handleNamespace(prefix: String, uri: String): Unit = {
				store.setNamespace(prefix, uri)
			}
		})

		Using.resource(new FileInputStream(file)) { input =>
			parser.parse(input)
		}
	}

	def save(file: File, store: NTriplesSailStore): Unit = {
		// Ensure parent directory exists
		file.getParentFile.mkdirs()

		// Write to temp file first for atomicity
		val tempFile = new File(file.getParentFile, file.getName + ".tmp")

		Using.resource(new FileOutputStream(tempFile)) { output =>
			val writer = Rio.createWriter(RDFFormat.TRIG, output)
			writer.startRDF()

			// Write namespaces
			val nsIter = store.getNamespaces()
			try {
				while (nsIter.hasNext) {
					val ns = nsIter.next()
					writer.handleNamespace(ns.getPrefix, ns.getName)
				}
			} finally {
				nsIter.close()
			}

			// Write statements
			val stIter = store.getAllStatements()
			try {
				while (stIter.hasNext) {
					writer.handleStatement(stIter.next())
				}
			} finally {
				stIter.close()
			}

			writer.endRDF()
		}

		// Atomic rename
		Files.move(tempFile.toPath, file.toPath, StandardCopyOption.REPLACE_EXISTING)
	}
}
