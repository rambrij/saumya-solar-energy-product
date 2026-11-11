package com.saumyasolar.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.NotificationDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class MailpitEmailNotificationAdapter implements NotificationChannelAdapter {
 private final JavaMailSender sender; private final String from; private final String baseUrl; private final HttpClient http; private final ObjectMapper mapper;
 public MailpitEmailNotificationAdapter(JavaMailSender sender,@Value("${app.mail.from:no-reply@saumya.local}") String from,@Value("${app.mailpit.base-url:http://mailpit:8025}") String baseUrl,ObjectMapper mapper){this.sender=sender;this.from=from;this.baseUrl=baseUrl.replaceAll("/+$","");this.mapper=mapper;this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();}
 public boolean supports(String channel){return "EMAIL".equals(channel);} public String providerCode(){return "MAILPIT";}
 public void dispatch(NotificationDelivery d) throws Exception {
  String key=d.getIdempotencyKey()==null?d.getId().toString():d.getIdempotencyKey();
  if(alreadyCaptured(key))return;
  MimeMessage message=sender.createMimeMessage();MimeMessageHelper helper=new MimeMessageHelper(message,false,StandardCharsets.UTF_8.name());
  helper.setFrom(from);helper.setTo(d.getRecipient());helper.setSubject(d.getSubject());helper.setText(d.getMessageBody()==null?"":d.getMessageBody(),false);
  message.setHeader("Message-ID","<"+key+"@saumya.local>");d.setProviderMessageId("<"+key+"@saumya.local>");sender.send(message);
 }
 private boolean alreadyCaptured(String key) throws Exception {
  String query=URLEncoder.encode("message-id:"+key+"@saumya.local",StandardCharsets.UTF_8);
  HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+"/api/v1/search?query="+query)).timeout(Duration.ofSeconds(5)).GET().build();
  HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
  if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Mailpit idempotency lookup failed with HTTP "+response.statusCode());
  return mapper.readTree(response.body()).path("messages_count").asInt(0)>0;
 }
}