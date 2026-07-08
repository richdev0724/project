package com.marketmono.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "marketmono.uw.shed-503")
@Getter
@Setter
public class UnusualWhalesShed503Properties {

    private boolean enabled = false;
}
