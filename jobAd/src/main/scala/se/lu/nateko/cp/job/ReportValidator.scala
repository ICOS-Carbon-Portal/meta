package se.lu.nateko.cp.job

import scala.util.Try
import java.util.UUID

object ReportValidator {

	val WAIT_TIME = 5
	private val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$""".r

	def validate(report: Report): Try[CandidateInfo] = for{
		_ <- validateCandidateInfo(report.candidateInfo);
		_ <- validateTime(report.assignmentId);
		_ <- validateAnswer(report)
	} yield report.candidateInfo

	private def validateTime(id: UUID): Try[Unit] = Try{
		if(id.version != 1) throw new Exception("Wrong assignment id!")
		val currentTimestamp = AssignmentGenerator.makeId.timestamp
		val elapsedSeconds = (currentTimestamp - id.timestamp).toDouble / 1e7
		if(elapsedSeconds <= 0) throw new Exception("Assignment from the future? Are you cheating?")
		if(elapsedSeconds >= WAIT_TIME) throw new Exception("Too late!")
	}

	private def validateAnswer(report: Report): Try[Unit] = Try{
		val id = report.assignmentId
		val question = AssignmentGenerator.makeAssignment(id).question
		val answer = report.answer
		if(question.country != answer.country) throw new Exception("Wrong country!")
		val perCapita = question.annualCO2 * 1000 / question.population
		val deviation = Math.abs(perCapita - answer.annualCO2PerCapita) / perCapita
		if(deviation > 1e-4) throw new Exception("Wrong numeric value!")
	}

	private def validateCandidateInfo(info: CandidateInfo): Try[Unit] = Try{
		if(info.firstName.isEmpty) throw new Exception("First name was empty!")
		if(info.lastName.isEmpty) throw new Exception("Last name was empty!")
		if(emailRegex.findFirstIn(info.email).isEmpty) throw new Exception("Wrong email address!")
	}

}
