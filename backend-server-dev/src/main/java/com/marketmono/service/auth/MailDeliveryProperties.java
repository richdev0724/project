package com.marketmono.service.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailDeliveryProperties {
    private String provider = "smtp";
    private String fromEmail = "noreply@marketmono.com";
    private String fromName = "Market Mono";
    private final GmailApi gmailApi = new GmailApi();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public GmailApi getGmailApi() {
        return gmailApi;
    }

    public static class GmailApi {
        private String serviceAccountJsonBase64;
        private String serviceAccountJson;
        private String serviceAccountJsonPath;
        private String delegatedUser;
        private String tokenUri = "https://oauth2.googleapis.com/token";
        private String apiBaseUrl = "https://gmail.googleapis.com/gmail/v1";

        public String getServiceAccountJsonBase64() {
            return serviceAccountJsonBase64;
        }

        public void setServiceAccountJsonBase64(String serviceAccountJsonBase64) {
            this.serviceAccountJsonBase64 = serviceAccountJsonBase64;
        }

        public String getServiceAccountJson() {
            return serviceAccountJson;
        }

        public void setServiceAccountJson(String serviceAccountJson) {
            this.serviceAccountJson = serviceAccountJson;
        }

        public String getServiceAccountJsonPath() {
            return serviceAccountJsonPath;
        }

        public void setServiceAccountJsonPath(String serviceAccountJsonPath) {
            this.serviceAccountJsonPath = serviceAccountJsonPath;
        }

        public String getDelegatedUser() {
            return delegatedUser;
        }

        public void setDelegatedUser(String delegatedUser) {
            this.delegatedUser = delegatedUser;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }
}
