package se.lu.nateko.cp.job

import java.util.UUID

case class Question(country: String, annualCO2: Double, population: Int)

case class Answer(country: String, annualCO2PerCapita: Double)

case class CandidateInfo(firstName: String, lastName: String, email: String)

case class Assignment(id: UUID, question: Question)

case class Report(assignmentId: UUID, answer: Answer, candidateInfo: CandidateInfo)

//case class ValidationResult(summary: String, candidate: CandidateInfo)