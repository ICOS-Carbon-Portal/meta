package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import java.io.{File, FileInputStream}
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
		// No-op - read-only store, no writes to disk
	}
}
