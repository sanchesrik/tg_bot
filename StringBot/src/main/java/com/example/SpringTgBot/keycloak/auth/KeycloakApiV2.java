package com.example.SpringTgBot.keycloak.auth;

import com.github.scribejava.apis.KeycloakApi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class KeycloakApiV2 extends KeycloakApi {
  private static final ConcurrentMap<String, KeycloakApi> INSTANCES = new ConcurrentHashMap<>();
  protected KeycloakApiV2(String baseUrlWithRealm) {
    super(baseUrlWithRealm);
  }

  protected static String composeBaseUrlWithRealm(String baseUrl, String realm) {
    return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "realms/" + realm;
  }
  public static KeycloakApi instance(String baseUrl, String realm) {
    final String defaultBaseUrlWithRealm = composeBaseUrlWithRealm(baseUrl, realm);

    //java8: switch to ConcurrentMap::computeIfAbsent
    KeycloakApi api = INSTANCES.get(defaultBaseUrlWithRealm);
    if (api == null) {
      api = new KeycloakApiV2(defaultBaseUrlWithRealm);
      final KeycloakApi alreadyCreatedApi = INSTANCES.putIfAbsent(defaultBaseUrlWithRealm, api);
      if (alreadyCreatedApi != null) {
        return alreadyCreatedApi;
      }
    }
    return api;
  }
}
