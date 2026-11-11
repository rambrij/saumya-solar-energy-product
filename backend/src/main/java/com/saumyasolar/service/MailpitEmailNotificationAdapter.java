package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class MailpitEmailNotificationAdapter implements NotificationChannelAdapter {
 private final JavaMailSender sender; private final String from;
 public MailpitEmailNotificationAdapter(JavaMailSender sender,@Value("${app.mail.from:no-reply@saumya.local}") String from){this.sender=sender;this.from=from;}
 public String channel(){return "EMAIL";} public String providerCode(){return "MAILPIT";}
 public void dispatch(NotificationDelivery d){SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(d.getRecipient());m.setSubject(d.getSubject());m.setText(d.getMessageBody());sender.send(m);}
}