package com.example.shortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

import static com.example.shortener.service.LogSanitizer.clean;

/**
 * Sends email alerts when an incident is recorded.
 *
 * <p>Disabled by default. Set {@code shortener.incident.notification.enabled=true} and configure
 * an SMTP server via {@code spring.mail.*} properties to activate. If no SMTP server is configured,
 * notifications are silently skipped regardless of the enabled flag.
 *
 * <p>Priority mapping:
 * <ul>
 *   <li>P1_CRITICAL / P2_HIGH → HIGH — Attention Required</li>
 *   <li>P3_MEDIUM             → MEDIUM — Review Recommended</li>
 *   <li>P4_LOW                → LOW — For Your Information</li>
 * </ul>
 */
@Service
public class IncidentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentNotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String to;
    private final String from;

    public IncidentNotificationService(
            Optional<JavaMailSender> mailSender,
            @Value("${shortener.incident.notification.enabled:false}") boolean enabled,
            @Value("${shortener.incident.notification.to:}") String to,
            @Value("${shortener.incident.notification.from:noreply@shortener-service}") String from) {
        this.mailSender = mailSender.orElse(null);
        this.enabled = enabled;
        this.to = to;
        this.from = from;
    }

    /**
     * Sends an incident notification email. Failures are logged and swallowed — the caller's
     * HTTP response must not be affected by a broken SMTP connection.
     *
     * @param severity  the operational priority of this incident
     * @param errorCode the error code or exception class name that triggered the incident
     * @param requestId the correlation ID of the request that caused the incident (may be null)
     */
    public void notify(IncidentSeverity severity, String errorCode, String requestId) {
        if (!enabled || mailSender == null) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject(severity));
            message.setText(body(severity, errorCode, requestId));
            mailSender.send(message);
            log.info("Incident notification sent: severity={} errorCode={} requestId={}",
                    severity, clean(errorCode), clean(requestId));
        } catch (MailException e) {
            // Log message only — full SMTP stack trace adds noise without diagnostic value here.
            log.error("Failed to send incident notification: severity={} requestId={} reason={}",
                    severity, clean(requestId), e.getMessage());
        }
    }

    private static String subject(IncidentSeverity severity) {
        return "[INCIDENT] " + priorityLabel(severity) + " | shortener-service";
    }

    private static String body(IncidentSeverity severity, String errorCode, String requestId) {
        return "Priority  : " + priorityLabel(severity) + "\n"
             + "Severity  : " + severity.name() + "\n"
             + "Error     : " + errorCode + "\n"
             + "Service   : shortener-service\n"
             + "Request ID: " + requestId + "\n"
             + "Time      : " + Instant.now() + "\n"
             + "\n"
             + actionMessage(severity);
    }

    static String priorityLabel(IncidentSeverity severity) {
        return switch (severity) {
            case P1_CRITICAL, P2_HIGH -> "HIGH — Attention Required";
            case P3_MEDIUM            -> "MEDIUM — Review Recommended";
            case P4_LOW               -> "LOW — For Your Information";
        };
    }

    private static String actionMessage(IncidentSeverity severity) {
        return switch (severity) {
            case P1_CRITICAL, P2_HIGH -> "This incident requires immediate attention. Please investigate promptly.";
            case P3_MEDIUM            -> "This incident is worth investigating within one business day.";
            case P4_LOW               -> "This is informational. Review when convenient.";
        };
    }
}
