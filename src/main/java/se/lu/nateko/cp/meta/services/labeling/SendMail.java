package se.lu.nateko.cp.meta.services.labeling;


import org.apache.commons.mail.*;

public class SendMail {

	public static void sendTxt(String from, String[] to, String subject, String body){
		sendTxt(from, to, subject, body, new String[0], new String[0]);
	}

	public static void sendTxt(String from, String[] to, String subject, String body, String[] cc){
		sendTxt(from, to, subject, body, cc, new String[0]);
	}

	public static void sendTxt(String from, String[] to, String subject, String body, String[] cc, String[] bcc){
		Email email = new SimpleEmail();
		email.setHostName("mail.lu.se");

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
}
