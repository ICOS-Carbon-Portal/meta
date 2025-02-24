package se.lu.nateko.cp.job

import java.util.UUID

final case class Question(country: String, annualCO2: Double, population: Int)

final case class Answer(country: String, annualCO2PerCapita: Double)

final case class CandidateInfo(firstName: String, lastName: String, email: String)

final case class Assignment(id: UUID, question: Question)

final case class Report(assignmentId: UUID, answer: Answer, candidateInfo: CandidateInfo)

//final case class ValidationResult(summary: String, candidate: CandidateInfo)