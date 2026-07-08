package com.marketmono.service.auth;

import com.marketmono.entity.auth.EmailVerificationToken;
import com.marketmono.entity.auth.PasswordResetToken;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.support.SupportTicket;
import com.marketmono.entity.support.SupportTicketMessage;
import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private RecordingEmailTransport emailTransport;
    private EmailTemplateRenderer emailTemplateRenderer;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailTransport = new RecordingEmailTransport();
        emailTemplateRenderer = mock(EmailTemplateRenderer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-13T10:15:00Z"), ZoneOffset.UTC);
        emailService = new EmailService(emailTransport, emailTemplateRenderer, clock);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://marketmono.com/");
    }

    @Test
    void shouldSendVerificationEmailWithTokenQueryLink() {
        User user = user("lead@example.com", "Lead User");
        EmailVerificationToken token = EmailVerificationToken.builder()
            .user(user)
            .token("verify-token")
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        emailService.sendVerificationEmail(user, token);

        assertThat(emailTransport.to).isEqualTo("lead@example.com");
        assertThat(emailTransport.subject).isEqualTo("Verify your Market Mono account");
        assertThat(emailTransport.html).isTrue();
        assertThat(emailTransport.content)
            .contains("https://marketmono.com/verify-email?token=verify-token")
            .contains("Verify Email Address");
    }

    @Test
    void shouldSendPasswordResetEmailWithCanonicalPathTokenLink() {
        User user = user("lead@example.com", "Lead User");
        PasswordResetToken token = PasswordResetToken.builder()
            .user(user)
            .token("reset-token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        emailService.sendPasswordResetEmail(user, token);

        assertThat(emailTransport.content)
            .contains("https://marketmono.com/reset-password/reset-token")
            .doesNotContain("/reset-password?token=");
    }

    @Test
    void shouldEncodeTokensInEmailLinks() {
        User user = user("lead@example.com", "Lead User");
        PasswordResetToken token = PasswordResetToken.builder()
            .user(user)
            .token("reset token/1")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        emailService.sendPasswordResetEmail(user, token);

        assertThat(emailTransport.content)
            .contains("https://marketmono.com/reset-password/reset%20token%2F1");
    }

    @Test
    void shouldDescribeCustomerInviteExpiryInHoursInsteadOfRawTimestamp() {
        User user = user("customer@example.com", "Customer User");
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("customer-invite"), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, String> variables = invocation.getArgument(1, Map.class);
                return new RenderedEmail(
                    "You've been invited to Market Mono",
                    "<p>This temporary password expires in " + variables.get("invite.expiresIn")
                        + ". Change it immediately after your first login.</p>",
                    "This temporary password expires in " + variables.get("invite.expiresIn")
                );
            });

        emailService.sendCustomerInviteEmail(
            user,
            "TempPass123!",
            LocalDateTime.of(2026, 5, 15, 10, 15)
        );

        assertThat(emailTransport.to).isEqualTo("customer@example.com");
        assertThat(emailTransport.subject).isEqualTo("You've been invited to Market Mono");
        assertThat(emailTransport.content)
            .contains("This temporary password expires in 48 hours.")
            .contains("Change it immediately after your first login.")
            .doesNotContain("2026-05-15T10:15");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("customer-invite"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("user.firstName", "Customer")
            .containsEntry("user.lastName", "User")
            .containsEntry("user.displayName", "Customer User")
            .containsEntry("user.email", "customer@example.com")
            .containsEntry("invite.temporaryPassword", "TempPass123!")
            .containsEntry("invite.expiresIn", "48 hours")
            .containsEntry("app.frontendUrl", "https://marketmono.com")
            .containsEntry("app.signInUrl", "https://marketmono.com/sign-in")
            .containsEntry("app.year", "2026");
    }

    @Test
    void shouldSendBetaWaitlistInviteFromEditableTemplate() {
        User user = user("beta@example.com", "Beta Trader");
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("beta-waitlist-invite"), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, String> variables = invocation.getArgument(1, Map.class);
                return new RenderedEmail(
                    "Market Mono beta access",
                    "<p>Register at " + variables.get("app.signUpUrl") + " and use "
                        + variables.get("beta.promoCode") + "</p>",
                    "Register at " + variables.get("app.signUpUrl")
                );
            });

        emailService.sendBetaWaitlistInviteEmail(
            user,
            "BETAMM2026",
            3
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("beta-waitlist-invite"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("user.firstName", "Beta")
            .containsEntry("user.lastName", "Trader")
            .containsEntry("user.displayName", "Beta Trader")
            .containsEntry("user.email", "beta@example.com")
            .containsEntry("beta.promoCode", "BETAMM2026")
            .containsEntry("beta.trialDays", "3")
            .containsEntry("beta.discountText", "10% off your first 3 months")
            .containsEntry("app.frontendUrl", "https://marketmono.com")
            .containsEntry("app.signInUrl", "https://marketmono.com/sign-in")
            .containsEntry("app.signUpUrl", "https://marketmono.com/sign-up")
            .containsEntry("app.subscribeUrl", "https://marketmono.com/subscribe?promoCode=BETAMM2026")
            .containsEntry("app.betaSubscribeUrl", "https://marketmono.com/subscribe?promoCode=BETAMM2026")
            .containsEntry("beta.checkoutUrl", "https://marketmono.com/subscribe?promoCode=BETAMM2026")
            .containsEntry("app.year", "2026")
            .doesNotContainKeys("invite.temporaryPassword", "invite.expiresIn");
        assertThat(emailTransport.to).isEqualTo("beta@example.com");
        assertThat(emailTransport.subject).isEqualTo("Market Mono beta access");
        assertThat(emailTransport.html).isTrue();
        assertThat(emailTransport.content)
            .contains("https://marketmono.com/sign-up")
            .contains("BETAMM2026")
            .doesNotContain("TempPass123!")
            .doesNotContain("temporary password");
    }

    @Test
    void shouldSendBetaWaitlistInviteWithoutDirectWhopCheckoutConfiguration() {
        User user = user("beta@example.com", "Beta Trader");
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("beta-waitlist-invite"), anyMap()))
            .thenReturn(new RenderedEmail(
                "Market Mono beta access",
                "<p>Use your invite link</p>",
                "Use your invite link"
            ));

        emailService.sendBetaWaitlistInviteEmail(user, "BETAMM2026", 3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("beta-waitlist-invite"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("beta.checkoutUrl", "https://marketmono.com/subscribe?promoCode=BETAMM2026")
            .doesNotContainValue("https://whop.com/checkout/beta-plan");
        assertThat(emailTransport.to).isEqualTo("beta@example.com");
    }

    @Test
    void shouldSendWaitlistConfirmationFromEditableTemplate() {
        User user = user("lead@example.com", "Lead User");
        user.setWaitlistSource("landing");
        user.setWaitlistJoinedAt(LocalDateTime.of(2026, 5, 12, 9, 30));
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("waitlist-confirmation"), anyMap()))
            .thenReturn(new RenderedEmail(
                "Welcome Lead",
                "<p>Welcome Lead User</p>",
                "Welcome Lead User"
            ));

        emailService.sendWaitlistConfirmationEmail(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("waitlist-confirmation"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("user.firstName", "Lead")
            .containsEntry("user.lastName", "User")
            .containsEntry("user.displayName", "Lead User")
            .containsEntry("user.email", "lead@example.com")
            .containsEntry("waitlist.source", "landing")
            .containsEntry("waitlist.joinedDate", "May 12, 2026")
            .containsEntry("app.frontendUrl", "https://marketmono.com")
            .containsEntry("app.year", "2026");
        assertThat(emailTransport.to).isEqualTo("lead@example.com");
        assertThat(emailTransport.subject).isEqualTo("Welcome Lead");
        assertThat(emailTransport.html).isTrue();
        assertThat(emailTransport.content).isEqualTo("<p>Welcome Lead User</p>");
    }

    @Test
    void shouldSendStaffReplySupportEmailFromEditableTemplate() {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setTicketNumber("MM-0127");
        ticket.setSubject("Billing question");
        ticket.setStatus(SupportTicketStatus.PENDING_RESPONSE);
        ticket.setPriority(SupportTicketPriority.MEDIUM);
        SupportTicketMessage message = SupportTicketMessage.staffReply(
            ticket,
            UUID.randomUUID(),
            "Agent Alice",
            "We adjusted your invoice and it is ready to review.",
            LocalDateTime.of(2026, 6, 3, 12, 0)
        );
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("support-ticket-staff-reply"), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, String> variables = invocation.getArgument(1, Map.class);
                return new RenderedEmail(
                    "Support update for " + variables.get("ticket.number"),
                    "<p>" + variables.get("message.preview") + "</p><a href=\""
                        + variables.get("ticket.url") + "\">View</a>",
                    variables.get("message.preview")
                );
            });

        emailService.sendSupportTicketStaffReplyEmail("customer@example.com", ticket, message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("support-ticket-staff-reply"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("ticket.number", "MM-0127")
            .containsEntry("ticket.subject", "Billing question")
            .containsEntry("message.preview", "We adjusted your invoice and it is ready to review.")
            .containsEntry("ticket.url", "https://marketmono.com/settings?supportTicket=MM-0127")
            .containsEntry("app.frontendUrl", "https://marketmono.com");
        assertThat(emailTransport.to).isEqualTo("customer@example.com");
        assertThat(emailTransport.subject).isEqualTo("Support update for MM-0127");
        assertThat(emailTransport.html).isTrue();
    }

    @Test
    void shouldSendTicketCreatedSupportEmailFromEditableTemplate() {
        SupportTicket ticket = supportTicket("MM-0128", "Login issue");
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "I cannot sign in.",
            LocalDateTime.of(2026, 6, 3, 12, 0)
        );
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("support-ticket-created"), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, String> variables = invocation.getArgument(1, Map.class);
                return new RenderedEmail(
                    "New support ticket " + variables.get("ticket.number"),
                    "<p>" + variables.get("user.email") + "</p>",
                    variables.get("message.preview")
                );
            });

        emailService.sendSupportTicketCreatedEmail("support@marketmono.com", ticket, message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("support-ticket-created"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("ticket.number", "MM-0128")
            .containsEntry("ticket.subject", "Login issue")
            .containsEntry("user.email", "customer@example.com")
            .containsEntry("message.preview", "I cannot sign in.");
        assertThat(emailTransport.to).isEqualTo("support@marketmono.com");
        assertThat(emailTransport.subject).isEqualTo("New support ticket MM-0128");
    }

    @Test
    void shouldSendUserReplySupportEmailFromEditableTemplate() {
        SupportTicket ticket = supportTicket("MM-0129", "Chart issue");
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "The chart still does not load.",
            LocalDateTime.of(2026, 6, 3, 12, 0)
        );
        org.mockito.Mockito.when(emailTemplateRenderer.render(eq("support-ticket-user-reply"), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, String> variables = invocation.getArgument(1, Map.class);
                return new RenderedEmail(
                    "Customer replied to " + variables.get("ticket.number"),
                    "<p>" + variables.get("message.preview") + "</p>",
                    variables.get("message.preview")
                );
            });

        emailService.sendSupportTicketUserReplyEmail("support@marketmono.com", ticket, message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateRenderer).render(eq("support-ticket-user-reply"), variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
            .containsEntry("ticket.number", "MM-0129")
            .containsEntry("ticket.subject", "Chart issue")
            .containsEntry("message.preview", "The chart still does not load.");
        assertThat(emailTransport.to).isEqualTo("support@marketmono.com");
        assertThat(emailTransport.subject).isEqualTo("Customer replied to MM-0129");
    }

    private User user(String email, String displayName) {
        User user = User.builder()
            .displayName(displayName)
            .build();
        user.setEmail(email);
        return user;
    }

    private static SupportTicket supportTicket(String ticketNumber, String subject) {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setTicketNumber(ticketNumber);
        ticket.setSubject(subject);
        ticket.setUserUuid(UUID.randomUUID());
        ticket.setUserEmail("customer@example.com");
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setPriority(SupportTicketPriority.MEDIUM);
        return ticket;
    }

    private static class RecordingEmailTransport implements EmailTransport {
        private String to;
        private String subject;
        private String content;
        private boolean html;

        @Override
        public void sendHtml(String to, String subject, String htmlContent) {
            this.to = to;
            this.subject = subject;
            this.content = htmlContent;
            this.html = true;
        }

        @Override
        public void sendPlainText(String to, String subject, String textContent) {
            this.to = to;
            this.subject = subject;
            this.content = textContent;
            this.html = false;
        }
    }
}
