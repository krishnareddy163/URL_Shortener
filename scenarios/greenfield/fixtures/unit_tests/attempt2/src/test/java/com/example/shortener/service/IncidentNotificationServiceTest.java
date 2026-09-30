package com.example.shortener.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncidentNotificationServiceTest {

    private static final String TO   = "ops@example.com";
    private static final String FROM = "noreply@shortener-service";

    private IncidentNotificationService enabled(JavaMailSender sender) {
        return new IncidentNotificationService(Optional.of(sender), true, TO, FROM);
    }

    @Test
    void doesNotSendWhenDisabled() {
        JavaMailSender sender = mock(JavaMailSender.class);
        IncidentNotificationService service =
                new IncidentNotificationService(Optional.of(sender), false, TO, FROM);

        service.notify(IncidentSeverity.P1_CRITICAL, "CODE_GENERATION_FAILED", "req-1");

        verifyNoInteractions(sender);
    }

    @Test
    void doesNotSendWhenMailSenderNotConfigured() {
        IncidentNotificationService service =
                new IncidentNotificationService(Optional.empty(), true, TO, FROM);

        assertThatNoException().isThrownBy(
                () -> service.notify(IncidentSeverity.P2_HIGH, "RATE_LIMITED", "req-2"));
    }

    @Test
    void p1CriticalSendsHighPriorityEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        enabled(sender).notify(IncidentSeverity.P1_CRITICAL, "CODE_GENERATION_FAILED", "req-3");

        verify(sender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getSubject()).contains("[INCIDENT]", "HIGH", "Attention Required");
        assertThat(msg.getText()).contains("P1_CRITICAL", "CODE_GENERATION_FAILED", "req-3", "HIGH");
        assertThat(msg.getTo()).containsExactly(TO);
        assertThat(msg.getFrom()).isEqualTo(FROM);
    }

    @Test
    void p2HighAlsoMapsToHighPriorityEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        enabled(sender).notify(IncidentSeverity.P2_HIGH, "RATE_LIMITED", "req-4");

        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("HIGH", "Attention Required");
        assertThat(captor.getValue().getText()).contains("immediate attention");
    }

    @Test
    void p3MediumSendsMediumPriorityEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        enabled(sender).notify(IncidentSeverity.P3_MEDIUM, "LINK_NOT_FOUND", "req-5");

        verify(sender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getSubject()).contains("[INCIDENT]", "MEDIUM", "Review Recommended");
        assertThat(msg.getText()).contains("P3_MEDIUM", "LINK_NOT_FOUND", "one business day");
    }

    @Test
    void p4LowSendsLowPriorityEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        enabled(sender).notify(IncidentSeverity.P4_LOW, "INVALID_URL", "req-6");

        verify(sender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getSubject()).contains("[INCIDENT]", "LOW", "For Your Information");
        assertThat(msg.getText()).contains("P4_LOW", "INVALID_URL", "informational");
    }

    @Test
    void mailExceptionDoesNotPropagateToCallerAndIsLogged() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP refused")).when(sender).send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(
                () -> enabled(sender).notify(IncidentSeverity.P1_CRITICAL, "CODE_GENERATION_FAILED", "req-7"));
    }

    @Test
    void priorityLabelCoversAllSeverities() {
        assertThat(IncidentNotificationService.priorityLabel(IncidentSeverity.P1_CRITICAL)).contains("HIGH");
        assertThat(IncidentNotificationService.priorityLabel(IncidentSeverity.P2_HIGH)).contains("HIGH");
        assertThat(IncidentNotificationService.priorityLabel(IncidentSeverity.P3_MEDIUM)).contains("MEDIUM");
        assertThat(IncidentNotificationService.priorityLabel(IncidentSeverity.P4_LOW)).contains("LOW");
    }
}
