package com.marketmono.service.auth;

import com.marketmono.entity.email.EmailTemplate;
import com.marketmono.repository.email.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    private final EmailTemplateRepository templateRepository;

    public RenderedEmail render(String slug, Map<String, String> variables) {
        EmailTemplate template = templateRepository.findBySlug(slug)
            .orElseThrow(() -> new IllegalStateException("Email template not found: " + slug));

        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new IllegalStateException("Email template is inactive: " + slug);
        }

        Map<String, String> safeVariables = variables != null ? variables : Map.of();
        return new RenderedEmail(
            replaceVariables(template.getSubject(), safeVariables),
            replaceVariables(template.getBodyHtml(), safeVariables, true),
            replaceVariables(template.getBodyText(), safeVariables)
        );
    }

    private String replaceVariables(String content, Map<String, String> variables) {
        return replaceVariables(content, variables, false);
    }

    private String replaceVariables(String content, Map<String, String> variables, boolean escapeHtml) {
        if (content == null) {
            return null;
        }

        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, escapeHtml ? escapeHtml(value) : value);
        }
        return result;
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
