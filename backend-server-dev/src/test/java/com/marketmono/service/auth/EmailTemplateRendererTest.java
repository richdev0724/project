package com.marketmono.service.auth;

import com.marketmono.entity.email.EmailTemplate;
import com.marketmono.repository.email.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailTemplateRendererTest {

    private EmailTemplateRepository templateRepository;
    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        templateRepository = mock(EmailTemplateRepository.class);
        renderer = new EmailTemplateRenderer(templateRepository);
    }

    @Test
    void shouldRenderActiveTemplateWithProvidedVariables() {
        when(templateRepository.findBySlug("waitlist-confirmation"))
            .thenReturn(Optional.of(EmailTemplate.builder()
                .slug("waitlist-confirmation")
                .subject("Welcome {{user.firstName}} {{user.lastName}}")
                .bodyHtml("<p>{{user.displayName}} joined in {{app.year}}</p>")
                .bodyText("Source: {{waitlist.source}} on {{waitlist.joinedDate}} via {{app.frontendUrl}}")
                .isActive(true)
                .build()));

        RenderedEmail rendered = renderer.render("waitlist-confirmation", Map.of(
            "user.firstName", "Lead",
            "user.lastName", "User",
            "user.displayName", "Lead User",
            "waitlist.source", "landing",
            "waitlist.joinedDate", "May 12, 2026",
            "app.frontendUrl", "https://marketmono.com",
            "app.year", "2026"
        ));

        assertThat(rendered.subject()).isEqualTo("Welcome Lead User");
        assertThat(rendered.htmlBody()).isEqualTo("<p>Lead User joined in 2026</p>");
        assertThat(rendered.textBody()).isEqualTo("Source: landing on May 12, 2026 via https://marketmono.com");
    }

    @Test
    void shouldEscapeUserControlledSupportVariablesInHtmlOnly() {
        when(templateRepository.findBySlug("support-ticket-user-reply"))
            .thenReturn(Optional.of(EmailTemplate.builder()
                .slug("support-ticket-user-reply")
                .subject("Customer replied: {{ticket.subject}}")
                .bodyHtml("<p>Subject: {{ticket.subject}}</p><p>{{message.preview}}</p>")
                .bodyText("Subject: {{ticket.subject}}\nPreview: {{message.preview}}")
                .isActive(true)
                .build()));

        RenderedEmail rendered = renderer.render("support-ticket-user-reply", Map.of(
            "ticket.subject", "Breakout <img src=x onerror=alert(\"x\")> & issue",
            "message.preview", "</p><script>alert('body')</script>"
        ));

        assertThat(rendered.subject())
            .isEqualTo("Customer replied: Breakout <img src=x onerror=alert(\"x\")> & issue");
        assertThat(rendered.textBody())
            .isEqualTo("Subject: Breakout <img src=x onerror=alert(\"x\")> & issue\n"
                + "Preview: </p><script>alert('body')</script>");
        assertThat(rendered.htmlBody())
            .isEqualTo("<p>Subject: Breakout &lt;img src=x onerror=alert(&quot;x&quot;)&gt; &amp; issue</p>"
                + "<p>&lt;/p&gt;&lt;script&gt;alert(&#39;body&#39;)&lt;/script&gt;</p>");
    }

    @Test
    void shouldRejectInactiveTemplate() {
        when(templateRepository.findBySlug("waitlist-confirmation"))
            .thenReturn(Optional.of(EmailTemplate.builder()
                .slug("waitlist-confirmation")
                .subject("Welcome")
                .bodyHtml("<p>Welcome</p>")
                .isActive(false)
                .build()));

        assertThatThrownBy(() -> renderer.render("waitlist-confirmation", Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive");
    }
}
