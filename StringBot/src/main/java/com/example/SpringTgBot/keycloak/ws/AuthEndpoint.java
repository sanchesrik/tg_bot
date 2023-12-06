package com.example.SpringTgBot.keycloak.ws;


import com.example.SpringTgBot.config.BotPropeties;
import com.example.SpringTgBot.keycloak.auth.OidcService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
public class AuthEndpoint {
    private final URI botUri;
    private final OidcService oidcService;

    public AuthEndpoint(BotPropeties botProperties, OidcService oidcService) throws URISyntaxException {
        botUri = new URI("tg://resolve?domain=" + botProperties.getUsername());
        this.oidcService = oidcService;
    }


    @GetMapping("/auth")
    @ResponseBody
    public ResponseEntity auth(@RequestParam("state") String state, @RequestParam("code") String code) {
        return oidcService.completeAuth(state, code)
            .map(userInfo -> ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, String.valueOf(botUri)).build())
            .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Cannot complete authentication"));

    }
}
