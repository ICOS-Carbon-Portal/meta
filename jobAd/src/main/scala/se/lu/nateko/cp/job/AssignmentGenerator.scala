package se.lu.nateko.cp.job

import java.util.UUID
import com.fasterxml.uuid.EthernetAddress
import com.fasterxml.uuid.Generators
import scala.io.Source

object AssignmentGenerator {

	private val uuidGenerator = {
		val ethAddr = EthernetAddress.fromInterface()
		Generators.timeBasedGenerator(ethAddr)
	}

	def makeId: UUID = uuidGenerator.generate()

	def createAssignment: Assignment = makeAssignment(makeId)

	def makeAssignment(id: UUID) = Assignment(id, getQuestion(id))

	def getQuestion(id: UUID): Question = {
		questions((id.timestamp % questions.length).toInt)
	}

	private val questions: IndexedSeq[Question] = {
		val is = getClass.getResourceAsStream("/co2.txt")
		val lines = Source.fromInputStream(is, "UTF-8").getLines.drop(1)
		lines.map{line =>
			val cells = line.split('\t')
			Question(
				country = cells(0),
				annualCO2 = cells(1).toDouble,
				population = cells(2).toInt
			)
		}.toIndexedSeq
	}
}
