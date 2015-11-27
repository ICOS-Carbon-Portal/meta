package se.lu.nateko.cp.meta.mail

import javax.mail._
import javax.mail.internet._
import java.util.Date
import java.util.Properties
import org.apache.commons.io.IOUtils
import se.lu.nateko.cp.meta.{ConfigLoader, EmailConfig}

class SendMail(config: EmailConfig) {

	def send(	to: Seq[String],
				subject: String,
				body: String,
				isHtml: Boolean = true,
				cc: Seq[String] = Nil,
				bcc: Seq[String] = Nil): Unit ={

		if (config.mailSendingActive) {
			val message: Message = {
				val properties = new Properties()
				properties.put("mail.smtp.host", config.smtpServer)
				val session = Session.getDefaultInstance(properties, null)
				new MimeMessage(session)
			}

			message.setFrom(new InternetAddress(config.fromAddress))
			to.foreach(r => message.addRecipient(Message.RecipientType.TO, new InternetAddress(r)))
			cc.foreach(r => message.addRecipient(Message.RecipientType.CC, new InternetAddress(r)))
			bcc.foreach(r => message.addRecipient(Message.RecipientType.BCC, new InternetAddress(r)))

			message.setSentDate(new Date())
			message.setSubject(subject)

			// Set log address
			config.logBccAddress.foreach(
				recipient => message.addRecipient(Message.RecipientType.BCC, new InternetAddress(recipient))
			)

			if (isHtml) {
				message.setContent(body, "text/html; charset=utf-8")
			} else {
				message.setText(body)
			}

			Transport.send(message)
		}
	}
}

object SendMail{

	def default: SendMail = new SendMail(ConfigLoader.default.stationLabelingService.mailing)
	def apply(config: EmailConfig) = new SendMail(config)

	def getBody(templatePath: String, params: Map[String, String]): String = {
		var body = IOUtils.toString(getClass.getResourceAsStream(templatePath), "UTF-8")

		for((key, value) <- params){
			body = body.replaceAllLiterally(s"***$key***", value)
		}
		body
	}

}
