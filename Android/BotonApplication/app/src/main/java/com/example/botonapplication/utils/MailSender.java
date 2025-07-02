package com.example.botonapplication.utils;

import android.util.Log;

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


public class MailSender {
    private static final String TAG = "MailSender";

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "465";
    private static final String EMAIL_FROM = "tomasbranchesi@gmail.com";
    private static final String EMAIL_PASSWORD = "gcej yarh zghr rean";


    private static Properties getMailProperties() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.socketFactory.port", SMTP_PORT);
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.ssl.trust", SMTP_HOST);
        props.put("mail.debug", "true");
        return props;
    }

    public static void sendEmail(String toEmail, String subject, String body, EmailCallback callback) {
        new Thread(() -> {
            try {
                Session session = Session.getInstance(getMailProperties(), new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject);
                message.setText(body);

                Transport transport = session.getTransport("smtp");
                transport.connect(SMTP_HOST, Integer.parseInt(SMTP_PORT), EMAIL_FROM, EMAIL_PASSWORD);
                transport.sendMessage(message, message.getAllRecipients());
                transport.close();

                callback.onSuccess();
                Log.d(TAG, "Correo enviado exitosamente");
            } catch (Exception e) {
                String error = "Error al enviar correo: " + e.getMessage();
                Log.e(TAG, error, e);
                callback.onError(error);
            }
        }).start();
    }

    public interface EmailCallback {
        void onSuccess();
        void onError(String error);
    }
}