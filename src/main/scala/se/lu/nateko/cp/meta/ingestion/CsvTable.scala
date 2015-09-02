package se.lu.nateko.cp.meta.ingestion

import java.io._
import au.com.bytecode.opencsv.CSVReader

trait TextTableRow extends IndexedSeq[String] {

	def apply(colName: String): String

}

trait TextTable {

	def columnNames: Seq[String]
	def rows: Seq[TextTableRow]

}

trait ArrayTextTable extends TextTable{
	protected def arrays: Seq[Array[String]]

	override def rows: Seq[TextTableRow] = {
		val colNames = columnNames
		val nOfCols = colNames.length
		val colIndexLookup = colNames.zipWithIndex.toMap
		arrays.map{array =>
			assert(array.length == nOfCols, "TextTable must have the same number of columns in every row!")
			new ArrayTableRow(array, colIndexLookup)
		}
	}

	private class ArrayTableRow(row: Array[String], colIndexLookup: Map[String, Int]) extends TextTableRow{
		override def length = row.length
		def apply(i: Int) = row(i)
		def apply(colName: String) = row(colIndexLookup(colName))
	}
}

class TsvDataTable(ioReader: Reader) extends ArrayTextTable {

	def this(file: File) = this(new BufferedReader(new FileReader(file)))
	def this(is: InputStream) = this(new BufferedReader(new InputStreamReader(is, "UTF-8")))

	private val reader = new CSVReader(ioReader, '\t')

	override val columnNames: Seq[String] = reader.readNext().toSeq

	final override protected def arrays: Stream[Array[String]] = {
		val row = reader.readNext()
		if(row == null) {
			reader.close()
			Stream.empty
		}
		else Stream.cons(row, arrays)
	}

}