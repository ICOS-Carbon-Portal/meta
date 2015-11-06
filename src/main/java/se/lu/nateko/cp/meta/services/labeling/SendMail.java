package se.lu.nateko.cp.meta.services.labeling;


import org.apache.commons.mail.*;

public class SendMail {

	private static final String smtpServer = "mail.lu.se";
	private static final String adminMailAddress = "carbon.admin@nateko.lu.se";

	public static void sendMail(String from, String[] to, String subject, String body, boolean isHtml, String[] cc, String[] bcc){
		if (isHtml){
			sendHtml(from, to, subject, body, cc, bcc);
		} else {
			sendTxt(from, to, subject, body, cc, bcc);
		}
	}

	public static void sendMail(String from, String[] to, String subject, String body, boolean isHtml, String[] cc){
		if (isHtml){
			sendHtml(from, to, subject, body, cc, new String[0]);
		} else {
			sendTxt(from, to, subject, body, cc, new String[0]);
		}
	}

	public static void sendMail(String from, String[] to, String subject, String body, boolean isHtml){
		if (isHtml){
			sendHtml(from, to, subject, body, new String[0], new String[0]);
		} else {
			sendTxt(from, to, subject, body, new String[0], new String[0]);
		}
	}

	public static void sendMail(String from, String[] to, String subject, String body){
		sendTxt(from, to, subject, body, new String[0], new String[0]);
	}

	public static void sendSystemMail(String subject, String body){
		sendTxt(adminMailAddress, new String[]{adminMailAddress}, subject, body, new String[0], new String[0]);
	}

	private static void sendTxt(String from, String[] to, String subject, String body, String[] cc, String[] bcc){
		Email email = new SimpleEmail();
		email.setHostName(smtpServer);
		email.setBounceAddress(adminMailAddress);

		try {
			email.setFrom(from);
			for (String toStr : to) {
				email.addTo(toStr);
			}
			email.setSubject(subject);
			email.setMsg(body);

			for (String ccStr : cc) {
				email.addCc(ccStr);
			}

			for (String bccStr : bcc) {
				email.addBcc(bccStr);
			}

			email.send();
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}

	private static void sendHtml(String from, String[] to, String subject, String body, String[] cc, String[] bcc){
		HtmlEmail email = new HtmlEmail();
		email.setHostName(smtpServer);
		email.setBounceAddress(adminMailAddress);

		try {
			email.setFrom(from);
			for (String toStr : to) {
				email.addTo(toStr);
			}
			email.setSubject(subject);
			email.setHtmlMsg(body);

			for (String ccStr : cc) {
				email.addCc(ccStr);
			}

			for (String bccStr : bcc) {
				email.addBcc(bccStr);
			}

			email.send();
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}
}
