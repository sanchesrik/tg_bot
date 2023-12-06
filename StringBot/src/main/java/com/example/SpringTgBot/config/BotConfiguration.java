package com.example.SpringTgBot.config;

import com.example.SpringTgBot.keycloak.auth.OidcService;
import com.example.SpringTgBot.service.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BotConfiguration {

  private final TelegramBotsApi telegramBotsApi;
  private final TelegramBot bot;

  @EventListener({ContextRefreshedEvent.class})
  public void init() throws TelegramApiException {
    bot.execute(new SetMyCommands(List.of(
        new BotCommand("/start", "get start"),
        new BotCommand("/mydata", "get my info"),
        new BotCommand("/deletedata", "del my info"),
        new BotCommand("/help", "help info"),
        new BotCommand("/calendar", "inline keyboard")), new BotCommandScopeDefault(), null));
    try {
      telegramBotsApi.registerBot(bot);
    } catch (TelegramApiException e) {
      log.error(e.getMessage(), e);
    }
  }
}

