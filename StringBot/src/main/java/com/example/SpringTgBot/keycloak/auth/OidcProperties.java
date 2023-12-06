package com.example.SpringTgBot.keycloak.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "oidc.keycloak")
@Data
@Component
public class OidcProperties {
    private String clientId;
    private String clientSecret;
    private String callback;
    private String baseUrl;
    private String realm;
}
