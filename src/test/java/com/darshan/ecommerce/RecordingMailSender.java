package com.darshan.ecommerce;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.MDC;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Replaces SMTP in tests: messages are kept in memory together with the thread, correlation id
// and user that were active when they were sent, so tests can check what the async pool saw.
public class RecordingMailSender extends JavaMailSenderImpl {

    public record SentMail(SimpleMailMessage message, String thread, String correlationId, String user) {

        public String to() {
            return message.getTo()[0];
        }
    }

    private final List<SentMail> sent = new CopyOnWriteArrayList<>();
    private final Set<String> failingRecipients = ConcurrentHashMap.newKeySet();

    public List<SentMail> sentTo(String recipient) {
        return sent.stream().filter(mail -> mail.to().equals(recipient)).toList();
    }

    public void failFor(String recipient) {
        failingRecipients.add(recipient);
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (Object original : originalMessages) {
            SimpleMailMessage message = (SimpleMailMessage) original;
            if (failingRecipients.contains(message.getTo()[0])) {
                throw new MailSendException("Mail server refused the message");
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            sent.add(new SentMail(message, Thread.currentThread().getName(), MDC.get("correlationId"),
                    authentication == null ? null : authentication.getName()));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }
}
