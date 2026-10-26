package com.ietscroll.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.ietscroll.exception.BadRequestException;
import com.ietscroll.service.EmailService;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    // iet.scroll.in@gmail.com is the verified Brevo sender (verified under Senders, domains, IPs)
    private static final String FROM_EMAIL = "iet.scroll.in@gmail.com";

    public void sendEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_EMAIL);
        message.setTo(toEmail);
        message.setSubject("IET Scroll - Verification Code");
        message.setText("Your verification code is: " + otp + "\n\nThis code expires in 10 minutes.");
        try {
            mailSender.send(message);
            log.info("OTP email sent successfully to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMostSpecificCause().getMessage());
            throw new BadRequestException("Could not send OTP email. Please check your email address or try again later.");
        }
    }
}
