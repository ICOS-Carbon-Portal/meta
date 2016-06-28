package se.lu.nateko.cp.job

import spray.json._
import java.util.UUID

object JobAdJson extends DefaultJsonProtocol{

	implicit object uuidFormat extends JsonFormat[UUID]{

		def write(id: UUID) = JsString(id.toString)

		def read(value: JsValue): UUID = value match{
			case JsString(s) => try{
				UUID.fromString(s)
			}catch{
				case argExc: IllegalArgumentException =>
					deserializationError("Could not parse job id", argExc)
			}
			case _ => deserializationError("String representation of a job id is expected")
		}
	}

	implicit val questionFormat = jsonFormat3(Question)
	implicit val answerFormat = jsonFormat2(Answer)
	implicit val candidateInfoFormat = jsonFormat3(CandidateInfo)
	implicit val assignmentFormat = jsonFormat2(Assignment)
	implicit val reportFormat = jsonFormat3(Report)

}
