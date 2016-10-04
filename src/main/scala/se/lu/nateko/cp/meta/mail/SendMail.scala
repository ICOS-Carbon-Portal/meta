package se.lu.nateko.cp.meta.mail

import javax.mail._
import javax.mail.internet._
import java.util.Date
import java.util.Properties
import se.lu.nateko.cp.meta.{ConfigLoader, EmailConfig}
import org.slf4j.LoggerFactory

class SendMail(config: EmailConfig) {

	private val log = LoggerFactory.getLogger(getClass)

	def send(	to: Seq[String],
				subject: String,
				body: String,
				cc: Seq[String] = Nil,
				bcc: Seq[String] = Nil): Unit = {

		if (config.mailSendingActive) try{
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

			message.setContent(body, "text/html; charset=utf-8")

			Transport.send(message)
		}catch{
			case err: Throwable =>
				log.error("Mail sending failed", err)
				throw err
		}
	}
}

object SendMail{
	def default: SendMail = new SendMail(ConfigLoader.default.stationLabelingService.mailing)
	def apply(config: EmailConfig) = new SendMail(config)
}
