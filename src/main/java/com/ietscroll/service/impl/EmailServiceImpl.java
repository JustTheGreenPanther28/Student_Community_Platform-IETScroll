package com.ietscroll.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ietscroll.exception.BadRequestException;
import com.ietscroll.service.EmailService;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    private static final String FROM_EMAIL = "iet.scroll.in@gmail.com";
    private static final String FROM_NAME = "IET Scroll";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendEmail(String toEmail, String otp) {
        // If Brevo API key is available, use HTTPS REST API (works on Render where SMTP port 587 is blocked)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevoApi(toEmail, otp);
            return;
        }

        // Fallback to standard SMTP (works on localhost)
        sendViaSmtp(toEmail, otp);
    }

    private void sendViaBrevoApi(String toEmail, String otp) {
        try {
            Map<String, Object> body = Map.of(
                "sender", Map.of("name", FROM_NAME, "email", FROM_EMAIL),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "IET Scroll - Verification Code",
                "textContent", "Your verification code is: " + otp + "\n\nThis code expires in 10 minutes."
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("accept", "application/json")
                .header("api-key", brevoApiKey.trim())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("OTP email sent successfully via Brevo API to {}", toEmail);
            } else {
                log.error("Brevo API returned error status {}: {}", response.statusCode(), response.body());
                throw new BadRequestException("Could not send OTP email via Brevo API: " + response.body());
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send OTP email via Brevo API to {}: {}", toEmail, e.getMessage());
            throw new BadRequestException("Could not send OTP email. Please try again later.");
        }
    }

    private void sendViaSmtp(String toEmail, String otp) {
        if (mailSender == null) {
            throw new BadRequestException("Mail sender is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_EMAIL);
        message.setTo(toEmail);
        message.setSubject("IET Scroll - Verification Code");
        message.setText("Your verification code is: " + otp + "\n\nThis code expires in 10 minutes.");
        try {
            mailSender.send(message);
            log.info("OTP email sent successfully via SMTP to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send OTP email via SMTP to {}: {}", toEmail, e.getMostSpecificCause().getMessage());
            throw new BadRequestException("Could not send OTP email. Please check your email address or try again later.");
        }
    }
}
