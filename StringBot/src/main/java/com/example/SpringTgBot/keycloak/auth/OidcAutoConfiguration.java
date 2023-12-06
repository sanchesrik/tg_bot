package com.example.SpringTgBot.keycloak.auth;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
class OidcAutoConfiguration {
    @Bean
    OAuth20Service oAuthService(OidcProperties properties) {
        return new ServiceBuilder(properties.getClientId())
                .apiSecret(properties.getClientSecret())
                .defaultScope("openid offline_access")
                .callback(properties.getCallback())
                .build(KeycloakApiV2.instance(properties.getBaseUrl(), properties.getRealm()));
    }
}
