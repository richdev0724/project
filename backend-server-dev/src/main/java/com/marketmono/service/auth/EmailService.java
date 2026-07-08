package com.marketmono.service.auth;

import com.marketmono.entity.auth.EmailVerificationToken;
import com.marketmono.entity.auth.PasswordResetToken;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.support.SupportTicket;
import com.marketmono.entity.support.SupportTicketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Email service for sending authentication-related emails.
 */
@Service
@Slf4j
public class EmailService {

    private static final String WAITLIST_CONFIRMATION_TEMPLATE = "waitlist-confirmation";
    private static final String CUSTOMER_INVITE_TEMPLATE = "customer-invite";
    private static final String BETA_WAITLIST_INVITE_TEMPLATE = "beta-waitlist-invite";
    private static final String SUPPORT_TICKET_CREATED_TEMPLATE = "support-ticket-created";
    private static final String SUPPORT_TICKET_USER_REPLY_TEMPLATE = "support-ticket-user-reply";
    private static final String SUPPORT_TICKET_STAFF_REPLY_TEMPLATE = "support-ticket-staff-reply";
    private static final int SUPPORT_MESSAGE_PREVIEW_MAX_LENGTH = 240;
    private static final DateTimeFormatter WAITLIST_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    
    private final EmailTransport emailTransport;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final Clock clock;
    
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Autowired
    public EmailService(
            EmailTransport emailTransport,
            EmailTemplateRenderer emailTemplateRenderer) {
        this(emailTransport, emailTemplateRenderer, Clock.systemDefaultZone());
    }

    EmailService(
            EmailTransport emailTransport,
            EmailTemplateRenderer emailTemplateRenderer,
            Clock clock) {
        this.emailTransport = emailTransport;
        this.emailTemplateRenderer = emailTemplateRenderer;
        this.clock = clock;
    }
    
    /**
     * Send email verification email asynchronously.
     */
    @Async
    public void sendVerificationEmail(User user, EmailVerificationToken token) {
        String verifyUrl = frontendUrl("/verify-email?token=" + encode(token.getToken()));
        String subject = "Verify your Market Mono account";
        String content = buildVerificationEmailContent(user.getDisplayName(), verifyUrl);
        
        emailTransport.sendHtml(user.getEmail(), subject, content);
        log.info("Verification email sent to: {}", user.getEmail());
    }
    
    /**
     * Send password reset email asynchronously.
     */
    @Async
    public void sendPasswordResetEmail(User user, PasswordResetToken token) {
        String resetUrl = frontendUrl("/reset-password/" + encode(token.getToken()));
        String subject = "Reset your Market Mono password";
        String content = buildPasswordResetEmailContent(user.getDisplayName(), resetUrl);
        
        emailTransport.sendHtml(user.getEmail(), subject, content);
        log.info("Password reset email sent to: {}", user.getEmail());
    }

    /**
     * Send waitlist confirmation email synchronously so the caller can track send success.
     */
    public void sendWaitlistConfirmationEmail(User user) {
        RenderedEmail email = emailTemplateRenderer.render(
            WAITLIST_CONFIRMATION_TEMPLATE,
            waitlistTemplateVariables(user)
        );

        if (hasText(email.htmlBody())) {
            emailTransport.sendHtml(user.getEmail(), email.subject(), email.htmlBody());
        } else {
            emailTransport.sendPlainText(user.getEmail(), email.subject(), email.textBody());
        }
        log.info("Waitlist confirmation email sent to: {}", user.getEmail());
    }

    /**
     * Send a trusted internal admin email synchronously so backend-admin can abort
     * state changes when delivery fails.
     */
    public void sendInternalAdminEmail(String toEmail, String subject, String htmlContent, String textContent) {
        if (htmlContent != null && !htmlContent.isBlank()) {
            emailTransport.sendHtml(toEmail, subject, htmlContent);
        } else {
            emailTransport.sendPlainText(toEmail, subject, textContent != null ? textContent : "");
        }
        log.info("Internal admin email sent to: {}", toEmail);
    }

    /**
     * Send a customer account invite with the generated one-time temporary password.
     */
    public void sendCustomerInviteEmail(User user, String temporaryPassword, LocalDateTime expiresAt) {
        RenderedEmail email = emailTemplateRenderer.render(
            CUSTOMER_INVITE_TEMPLATE,
            customerInviteTemplateVariables(user, temporaryPassword, expiresAt)
        );

        if (hasText(email.htmlBody())) {
            emailTransport.sendHtml(user.getEmail(), email.subject(), email.htmlBody());
        } else {
            emailTransport.sendPlainText(user.getEmail(), email.subject(), email.textBody());
        }
        log.info("Customer invite email sent to: {}", user.getEmail());
    }

    /**
     * Send a beta waitlist invite that asks the recipient to register normally.
     */
    public void sendBetaWaitlistInviteEmail(User user, String promoCode, int trialDays) {
        RenderedEmail email = emailTemplateRenderer.render(
            BETA_WAITLIST_INVITE_TEMPLATE,
            betaWaitlistInviteTemplateVariables(user, promoCode, trialDays)
        );

        if (hasText(email.htmlBody())) {
            emailTransport.sendHtml(user.getEmail(), email.subject(), email.htmlBody());
        } else {
            emailTransport.sendPlainText(user.getEmail(), email.subject(), email.textBody());
        }
        log.info("Beta waitlist invite email sent to: {}", user.getEmail());
    }

    public void sendSupportTicketCreatedEmail(
            String toEmail,
            SupportTicket ticket,
            SupportTicketMessage message) {
        RenderedEmail email = emailTemplateRenderer.render(
            SUPPORT_TICKET_CREATED_TEMPLATE,
            supportTicketTemplateVariables(ticket, message)
        );
        sendRenderedEmail(toEmail, email);
        log.info("Support ticket created email sent to: {}", toEmail);
    }

    public void sendSupportTicketUserReplyEmail(
            String toEmail,
            SupportTicket ticket,
            SupportTicketMessage message) {
        RenderedEmail email = emailTemplateRenderer.render(
            SUPPORT_TICKET_USER_REPLY_TEMPLATE,
            supportTicketTemplateVariables(ticket, message)
        );
        sendRenderedEmail(toEmail, email);
        log.info("Support ticket user reply email sent to: {}", toEmail);
    }

    public void sendSupportTicketStaffReplyEmail(
            String toEmail,
            SupportTicket ticket,
            SupportTicketMessage message) {
        RenderedEmail email = emailTemplateRenderer.render(
            SUPPORT_TICKET_STAFF_REPLY_TEMPLATE,
            supportTicketTemplateVariables(ticket, message)
        );
        sendRenderedEmail(toEmail, email);
        log.info("Support ticket staff reply email sent to: {}", toEmail);
    }

    private String frontendUrl(String path) {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return path;
        }

        String baseUrl = frontendUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + path;
    }

    private Map<String, String> waitlistTemplateVariables(User user) {
        NameParts nameParts = splitDisplayName(user.getDisplayName());
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("user.firstName", nameParts.firstName());
        variables.put("user.lastName", nameParts.lastName());
        variables.put("user.displayName", displayNameOrDefault(user.getDisplayName()));
        variables.put("user.email", valueOrEmpty(user.getEmail()));
        variables.put("waitlist.source", valueOrEmpty(user.getWaitlistSource()));
        variables.put("waitlist.joinedDate", formatWaitlistJoinedDate(user.getWaitlistJoinedAt()));
        variables.put("app.frontendUrl", frontendBaseUrl());
        variables.put("app.year", String.valueOf(Year.now(clock).getValue()));
        return variables;
    }

    private Map<String, String> customerInviteTemplateVariables(
            User user,
            String temporaryPassword,
            LocalDateTime expiresAt) {
        NameParts nameParts = splitDisplayName(user.getDisplayName());
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("user.firstName", nameParts.firstName());
        variables.put("user.lastName", nameParts.lastName());
        variables.put("user.displayName", displayNameOrDefault(user.getDisplayName()));
        variables.put("user.email", valueOrEmpty(user.getEmail()));
        variables.put("invite.temporaryPassword", valueOrEmpty(temporaryPassword));
        variables.put("invite.expiresIn", formatCustomerInviteExpiryWindow(expiresAt));
        variables.put("app.frontendUrl", frontendBaseUrl());
        variables.put("app.signInUrl", frontendUrl("/sign-in"));
        variables.put("app.year", String.valueOf(Year.now(clock).getValue()));
        return variables;
    }

    private Map<String, String> betaWaitlistInviteTemplateVariables(
            User user,
            String promoCode,
            int trialDays) {
        NameParts nameParts = splitDisplayName(user.getDisplayName());
        String directBetaCheckoutUrl = betaSubscribeUrl(promoCode);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("user.firstName", nameParts.firstName());
        variables.put("user.lastName", nameParts.lastName());
        variables.put("user.displayName", displayNameOrDefault(user.getDisplayName()));
        variables.put("user.email", valueOrEmpty(user.getEmail()));
        variables.put("beta.promoCode", valueOrEmpty(promoCode));
        variables.put("beta.trialDays", String.valueOf(trialDays));
        variables.put("beta.discountText", "10% off your first 3 months");
        variables.put("app.frontendUrl", frontendBaseUrl());
        variables.put("app.signInUrl", frontendUrl("/sign-in"));
        variables.put("app.signUpUrl", frontendUrl("/sign-up"));
        variables.put("app.subscribeUrl", frontendUrl("/subscribe?promoCode=" + encode(valueOrEmpty(promoCode))));
        variables.put("app.betaSubscribeUrl", directBetaCheckoutUrl);
        variables.put("beta.checkoutUrl", directBetaCheckoutUrl);
        variables.put("app.year", String.valueOf(Year.now(clock).getValue()));
        return variables;
    }

    private String betaSubscribeUrl(String promoCode) {
        return frontendUrl("/subscribe?promoCode=" + encode(valueOrEmpty(promoCode)));
    }

    private Map<String, String> supportTicketTemplateVariables(SupportTicket ticket, SupportTicketMessage message) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("ticket.id", ticket.getId() != null ? ticket.getId().toString() : "");
        variables.put("ticket.number", valueOrEmpty(ticket.getTicketNumber()));
        variables.put("ticket.subject", valueOrEmpty(ticket.getSubject()));
        variables.put("ticket.status", ticket.getStatus() != null ? ticket.getStatus().name() : "");
        variables.put("ticket.priority", ticket.getPriority() != null ? ticket.getPriority().name() : "");
        variables.put("ticket.url", supportTicketUrl(ticket));
        variables.put("user.email", valueOrEmpty(ticket.getUserEmail()));
        variables.put("message.preview", supportMessagePreview(message));
        variables.put("app.frontendUrl", frontendBaseUrl());
        variables.put("app.year", String.valueOf(Year.now(clock).getValue()));
        return variables;
    }

    private NameParts splitDisplayName(String displayName) {
        String normalized = displayNameOrDefault(displayName);
        int separator = normalized.indexOf(' ');
        if (separator < 0) {
            return new NameParts(normalized, "");
        }
        return new NameParts(normalized.substring(0, separator), normalized.substring(separator + 1));
    }

    private String displayNameOrDefault(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "there";
        }
        return displayName.trim().replaceAll("\\s+", " ");
    }

    private String formatWaitlistJoinedDate(LocalDateTime joinedAt) {
        return joinedAt != null ? WAITLIST_DATE_FORMATTER.format(joinedAt) : "";
    }

    private String frontendBaseUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "";
        }
        String baseUrl = frontendUrl.trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void sendRenderedEmail(String toEmail, RenderedEmail email) {
        if (hasText(email.htmlBody())) {
            emailTransport.sendHtml(toEmail, email.subject(), email.htmlBody());
        } else {
            emailTransport.sendPlainText(toEmail, email.subject(), email.textBody());
        }
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String supportTicketUrl(SupportTicket ticket) {
        return frontendUrl("/settings?supportTicket=" + encode(valueOrEmpty(ticket.getTicketNumber())));
    }

    private String supportMessagePreview(SupportTicketMessage message) {
        if (message == null || message.getBody() == null) {
            return "";
        }
        String normalized = message.getBody().trim().replaceAll("\\s+", " ");
        if (normalized.length() <= SUPPORT_MESSAGE_PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUPPORT_MESSAGE_PREVIEW_MAX_LENGTH - 1) + "...";
    }

    private record NameParts(String firstName, String lastName) {}
    
    /**
     * Build HTML content for verification email.
     */
    private String buildVerificationEmailContent(String displayName, String verifyUrl) {
        String name = displayName != null ? displayName : "there";
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #1a1a2e; color: #fff; padding: 20px; text-align: center; }
                    .content { padding: 30px; background: #f9f9f9; }
                    .button { display: inline-block; background: #4f46e5; color: #fff !important; 
                              padding: 12px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Market Mono</h1>
                    </div>
                    <div class="content">
                        <h2>Welcome, %s!</h2>
                        <p>Thank you for signing up for Market Mono. Please verify your email address to activate your account.</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Verify Email Address</a>
                        </p>
                        <p>This link will expire in 24 hours.</p>
                        <p>If you didn't create an account, you can safely ignore this email.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2025 Market Mono. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(name, verifyUrl);
    }
    
    /**
     * Build HTML content for password reset email.
     */
    private String buildPasswordResetEmailContent(String displayName, String resetUrl) {
        String name = displayName != null ? displayName : "there";
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #1a1a2e; color: #fff; padding: 20px; text-align: center; }
                    .content { padding: 30px; background: #f9f9f9; }
                    .button { display: inline-block; background: #dc2626; color: #fff !important; 
                              padding: 12px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; }
                    .warning { background: #fef3c7; border-left: 4px solid #f59e0b; padding: 10px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Market Mono</h1>
                    </div>
                    <div class="content">
                        <h2>Password Reset Request</h2>
                        <p>Hi %s,</p>
                        <p>We received a request to reset your password. Click the button below to create a new password:</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Reset Password</a>
                        </p>
                        <p>This link will expire in 1 hour.</p>
                        <div class="warning">
                            <strong>Security Notice:</strong> If you didn't request this password reset, 
                            please ignore this email or contact support if you're concerned about your account security.
                        </div>
                    </div>
                    <div class="footer">
                        <p>&copy; 2025 Market Mono. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(name, resetUrl);
    }

    private String formatCustomerInviteExpiryWindow(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return "the configured invite window";
        }

        long minutes = Duration.between(LocalDateTime.now(clock), expiresAt).toMinutes();
        if (minutes <= 0) {
            return "less than 1 hour";
        }

        long hours = Math.max(1, (minutes + 59) / 60);
        return hours == 1 ? "1 hour" : hours + " hours";
    }
}
