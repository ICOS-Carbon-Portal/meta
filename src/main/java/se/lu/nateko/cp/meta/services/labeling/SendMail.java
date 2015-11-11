package se.lu.nateko.cp.meta.services.labeling;


import org.apache.commons.mail.*;

public class SendMail {

	private final String smtpServer;
	private final String fromAddress;
	private final String[] logBccAddress;


	public SendMail(String smtpServer, String fromAddress){
		this.smtpServer = smtpServer;
		this.fromAddress = fromAddress;
		this.logBccAddress = new String[]{};
	}

	public SendMail(String smtpServer, String fromAddress, String logBccAddress){
		this.smtpServer = smtpServer;
		this.fromAddress = fromAddress;
		this.logBccAddress = new String[]{logBccAddress};
	}

	public void sendMail(String[] to, String subject, String body, boolean isHtml, String[] cc, String[] bcc) throws EmailException {
		if (isHtml){
			sendHtml(to, subject, body, cc, bcc);
		} else {
			sendTxt(to, subject, body, cc, bcc);
		}
	}

	public void sendMail(String[] to, String subject, String body, boolean isHtml, String[] cc) throws EmailException {
		if (isHtml){
			sendHtml(to, subject, body, cc, new String[0]);
		} else {
			sendTxt(to, subject, body, cc, new String[0]);
		}
	}

	public void sendMail(String[] to, String subject, String body, boolean isHtml) throws EmailException {
		if (isHtml){
			sendHtml(to, subject, body, new String[0], new String[0]);
		} else {
			sendTxt(to, subject, body, new String[0], new String[0]);
		}
	}

	public void sendMail(String[] to, String subject, String body) throws EmailException {
		sendTxt(to, subject, body, new String[0], new String[0]);
	}

	public void sendSystemMail(String subject, String body) throws EmailException {
		sendTxt(new String[]{fromAddress}, subject, body, new String[0], new String[0]);
	}

	private void sendTxt(String[] to, String subject, String body, String[] cc, String[] bcc) throws EmailException {
		Email email = new SimpleEmail();
		email.setHostName(smtpServer);

		email.setFrom(fromAddress);
		for (String toStr : to) {
			email.addTo(toStr);
		}
		email.setSubject(subject);
		email.setMsg(body);

		for (String ccStr : cc) {
			email.addCc(ccStr);
		}

		for (String bccStr : logBccAddress) {
			email.addBcc(bccStr);
		}

		for (String bccStr : bcc) {
			email.addBcc(bccStr);
		}

		email.send();
	}

	private void sendHtml(String[] to, String subject, String body, String[] cc, String[] bcc) throws EmailException {
		HtmlEmail email = new HtmlEmail();
		email.setHostName(smtpServer);

		email.setFrom(fromAddress);
		for (String toStr : to) {
			email.addTo(toStr);
		}
		email.setSubject(subject);
		email.setHtmlMsg(body);

		for (String ccStr : cc) {
			email.addCc(ccStr);
		}

		for (String bccStr : logBccAddress) {
			email.addBcc(bccStr);
		}

		for (String bccStr : bcc) {
			email.addBcc(bccStr);
		}

		email.send();
	}
}
