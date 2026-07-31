package com.example.dawanow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dawanow.stripe")
@Getter
@Setter
public class StripeProperties {

    private String secretKey = "";
    private String webhookSecret = "";
    private String currency = "egp";
}
