package com.example.SpringTgBot.service;

import com.example.SpringTgBot.config.BotPropeties;
import com.example.SpringTgBot.jpa.entity.User;
import com.example.SpringTgBot.jpa.repository.UserRepository;
import com.example.SpringTgBot.keycloak.auth.OidcService;
import com.example.SpringTgBot.keycloak.auth.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DateFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
  private final UserRepository userRepository;
  private final BotPropeties config;
  private final OidcService oidcService;

  private String messageForUser = "";
  private static final String prevButton = "PREV_BUTTON: ";
  private static final String nextButton = "NEXT_BUTTON: ";
  private static final String backButton = "BACK_BUTTON";

  private static final String repliedToUser = "Replied to user: {}";
  private static final String saveUser = "User saved: {}";
  private static final String welcomeMessage = "Личность подтверждена, добро пожаловать, ";

  private List<String> dayOfWeeks() {
    List<String> weekDaysList = new ArrayList<>();
    for (String day : new DateFormatSymbols(new Locale("ru")).getShortWeekdays()) {
      weekDaysList.add(day.toUpperCase());
    }
    weekDaysList.subList(0, 1).clear();
    String firstElement = weekDaysList.remove(0);
    weekDaysList.add(firstElement);
    return weekDaysList;
  }

  @Override
  public String getBotUsername() {
    return config.getUsername();
  }

  @Override
  public String getBotToken() {
    return config.getToken();
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      String messageText = update.getMessage().getText();
      long chatId = update.getMessage().getChatId();
      var userId = update.getMessage().getFrom().getId();
      Map<String, Runnable> commandHandlers = Map.of(
          "/start", () -> {
            oidcService.findUserInfo(userId).ifPresentOrElse(
                userInfo -> greet(userInfo, update.getMessage() ),
                () -> askForLogin(userId, chatId));
          },
          "/mydata", () -> {
            prepareAndSendMessage(chatId, "ID пользователя " + update.getMessage().getChat().getId());
            prepareAndSendMessage(chatId, "ID сессии чата " + update.getMessage().getChatId());
          },
          "/help", () -> {
            prepareAndSendMessage(chatId, "Этот бот помогает запланировать и согласовать отпуск, взять соц день или сообщить о больничном.");
          },
          "Соц день", () -> {
            messageForUser = "Выберите дату соц дня. Нажмите пропустить если соц день необходим сегодня";
            calendar(chatId, messageForUser);
          },
          "Запланировать отпуск", () -> {
            messageForUser = "Выберите дату начала отпуска";
            calendar(chatId, messageForUser);
          },
          "Сообщить о больничном", () -> {
            messageForUser = "Выберите последний день больничного";
            calendar(chatId, messageForUser);
          }
      );
      commandHandlers.getOrDefault(messageText, () -> prepareAndSendMessage(chatId, "ID сессии чата " + update.getMessage().getChat().getId()))
          .run();
    } else if (update.hasCallbackQuery()) {
      String callBackData = update.getCallbackQuery().getData();
      long messageId = update.getCallbackQuery().getMessage().getMessageId();
      long chatId = update.getCallbackQuery().getMessage().getChatId();
      if (callBackData.replaceFirst("\\d{4}-\\d{1,2}-\\d{1,2}", "").equals(prevButton)) {
        executeEditCalendar(chatId, messageId, callBackData.replace(prevButton, ""),messageForUser);
      }
      else if (callBackData.replaceFirst("\\d{4}-\\d{1,2}-\\d{1,2}", "").equals(nextButton)) {
        executeEditCalendar(chatId, messageId, callBackData.replace(nextButton, ""),messageForUser);
      }
      else if (callBackData.equals(backButton)) {
        backAction(chatId, messageId);
      }
    }
  }
  private void greet(UserInfo userInfo,Message msg) {
    var username = userInfo.getPreferredUsername();
    var sub = userInfo.getSub();
    var message = String.format("Личность успешно подтверждена, добро пожаловать, " + username);
    if (userRepository.findById(msg.getChatId()).isEmpty()) {
      userRepository.save(User.builder()
          .chatId(msg.getChatId())
          .userName(sub)
          .build());
      log.info(saveUser, msg.getChat().getUserName());
    }
    sendMessage(msg.getChatId(), message);
  }

  private void askForLogin(Long userId, Long chatId) {
    var url = oidcService.getAuthUrl(userId);
    var message = String.format("Чтобы подтвердить личность, перейдите по <a href=\"%s\">ссылке</a>. После авторизации нажмите на команду /start еще раз для проверки данных", url);
    sendHtmlMessage(message, chatId);
  }

  void sendHtmlMessage(String message, Long chatId) {
    var sendMessage = new SendMessage(String.valueOf(chatId), message);
    sendMessage.setParseMode("HTML");
    executeMessage(sendMessage);
  }

  private void backAction(long chatId, long messageId) {
    EditMessageText message = new EditMessageText();
    message.setChatId(String.valueOf(chatId));
    message.setText("Выберите другой пункт меню ");
    message.setMessageId((int) messageId);
    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error(e.getMessage(), e);
    }
  }

  private void calendar(long chatId, String text) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(text);

    LocalDate localDate = LocalDate.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(new Locale("ru"));
    String currentMonth = StringUtils.capitalize(formatter.format(localDate));
    int year = localDate.getYear();
    int monthForOutput = localDate.getMonthValue();

    InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
    List<InlineKeyboardButton> rowInLine = new ArrayList<>();
    List<InlineKeyboardButton> rowForDays = new ArrayList<>();
    List<InlineKeyboardButton> action = new ArrayList<>();
    List<InlineKeyboardButton> backAction = new ArrayList<>();

    var button = new InlineKeyboardButton();
    button.setText(currentMonth);
    button.setCallbackData("NONE");
    rowInLine.add(button);
    rowsInLine.add(rowInLine);

    List<String> dayOfWeeksList = dayOfWeeks();
    for (String day : dayOfWeeksList) {
      var dayOfWeek = new InlineKeyboardButton();
      dayOfWeek.setText(day);
      dayOfWeek.setCallbackData("NONE");
      rowForDays.add(dayOfWeek);
    }
    rowsInLine.add(rowForDays);


    int maxDate = localDate.lengthOfMonth();
    String dates = "";
    localDate = LocalDate.of(year, monthForOutput, 1);
    for (int i = 1; i <= maxDate; i++) {
      int day = localDate.withDayOfMonth(i).getDayOfWeek().getValue();
      dates += String.valueOf(i);

      if (i < maxDate) {
        dates += ", ";
      }
      if (day == 7) {
        dates += ", - ";
      }
    }
    String[] dateGroups = dates.split(" - ");

    // Для каждой группы разделенной по дефису
    for (int i = 0; i < dateGroups.length; i++) {
      // Разделение каждой группы на список чисел по запятой и пробелу
      String[] dateArray = dateGroups[i].split(",\\s*");

      // Создание списка и добавление в него элементов массива
      List<InlineKeyboardButton> dateList = new ArrayList<>();

      for (String date : dateArray) {
        // Проверка на пустую строку
        if (!date.trim().isEmpty()) {
          var test = new InlineKeyboardButton();
          test.setText(date);
          test.setCallbackData("NONE");
          dateList.add(test);
        }
      }
      // Проверка на количество элементов в списке
      if (dateList.size() < 7 && i == 0) {
        while (dateList.size() < 7) {
          var empSt = new InlineKeyboardButton();
          empSt.setText(" ");
          empSt.setCallbackData("NONE");
          dateList.add(0, empSt);
        }
      }
      if (dateList.size() < 7) {
        while (dateList.size() < 7) {
          var empEnd = new InlineKeyboardButton();
          empEnd.setText(" ");
          empEnd.setCallbackData("NONE");
          dateList.add(empEnd);
        }
      }
      rowsInLine.add(dateList);
    }


    var previous = new InlineKeyboardButton();
    previous.setText("<<");
    previous.setCallbackData(prevButton + String.valueOf(LocalDate.of(year,monthForOutput,1).minusMonths(1)));
    action.add(previous);
    var next = new InlineKeyboardButton();
    next.setText(">>");
    next.setCallbackData(nextButton + String.valueOf(LocalDate.of(year,monthForOutput,1).plusMonths(1)));
    action.add(next);
    rowsInLine.add(action);

    var back = new InlineKeyboardButton();
    back.setText("Назад");
    back.setCallbackData(backButton);
    backAction.add(back);
    rowsInLine.add(backAction);

    markupInLine.setKeyboard(rowsInLine);
    message.setReplyMarkup(markupInLine);
    executeMessage(message);
  }

  private void registerUser(Message msg) {
    if (userRepository.findById(msg.getChatId()).isEmpty()) {
      userRepository.save(User.builder()
          .chatId(msg.getChatId())
          .userName(msg.getChat().getUserName())
          .build());
      log.info(saveUser, msg.getChat().getUserName());
    }
  }

  private void startCommandReceived(long chatId, String name) {
    log.info(repliedToUser, name);
    sendMessage(chatId, String.format(welcomeMessage + name));
  }

  private void sendMessage(long chatId, String textToSend) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(textToSend);
    ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
    List<KeyboardRow> keyboardRows = new ArrayList<>();
    KeyboardRow row = new KeyboardRow();
    row.add("Соц день");
    row.add("Запланировать отпуск");
    keyboardRows.add(row);
    row = new KeyboardRow();
    row.add("Сообщить о больничном");
    row.add("Мой календарь");
    keyboardRows.add(row);
    row = new KeyboardRow();
    row.add("Кого сегодня нет");
    row.add("Все функции");
    keyboardRows.add(row);
    row = new KeyboardRow();
    row.add("Сформировать выгрузку");
    row.add("???");
    keyboardRows.add(row);
    keyboardMarkup.setKeyboard(keyboardRows);
    message.setReplyMarkup(keyboardMarkup);
    executeMessage(message);
  }

  private void executeEditCalendar(Long chatId, long messageId, String locDate, String emptyText) {
    EditMessageText message = new EditMessageText();
    message.setChatId(String.valueOf(chatId));
    message.setText(emptyText);

    LocalDate localDate = LocalDate.parse(locDate);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(new Locale("ru"));
    String month = StringUtils.capitalize(formatter.format(localDate));
    int year = localDate.getYear();
    int monthForOutput = localDate.getMonthValue();

    InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
    List<InlineKeyboardButton> rowInLine = new ArrayList<>();
    List<InlineKeyboardButton> rowForDays = new ArrayList<>();
    List<InlineKeyboardButton> action = new ArrayList<>();
    List<InlineKeyboardButton> backAction = new ArrayList<>();

    var button = new InlineKeyboardButton();
    button.setText(month);
    button.setCallbackData("NONE");
    rowInLine.add(button);
    rowsInLine.add(rowInLine);

    List<String> dayOfWeeksList = dayOfWeeks();
    for (String day : dayOfWeeksList) {
      var dayOfWeek = new InlineKeyboardButton();
      dayOfWeek.setText(day);
      dayOfWeek.setCallbackData("NONE");
      rowForDays.add(dayOfWeek);
    }
    rowsInLine.add(rowForDays);

    int maxDate = localDate.lengthOfMonth();
    String dates = "";
    localDate = LocalDate.of(year, monthForOutput, 1);
    for (int i = 1; i <= maxDate; i++) {
      int day = localDate.withDayOfMonth(i).getDayOfWeek().getValue();
      dates += String.valueOf(i);

      if (i < maxDate) {
        dates += ", ";
      }
      if (day == 7) {
        dates += ", - ";
      }
    }
    String[] dateGroups = dates.split(" - ");

    // Для каждой группы разделенной по дефису
    for (int i = 0; i < dateGroups.length; i++) {
      // Разделение каждой группы на список чисел по запятой и пробелу
      String[] dateArray = dateGroups[i].split(",\\s*");

      // Создание списка и добавление в него элементов массива
      List<InlineKeyboardButton> dateList = new ArrayList<>();

      for (String date : dateArray) {
        // Проверка на пустую строку
        if (!date.trim().isEmpty()) {
          var test = new InlineKeyboardButton();
          test.setText(date);
          test.setCallbackData("NONE");
          dateList.add(test);
        }
      }
      // Проверка на количество элементов в списке
      if (dateList.size() < 7 && i == 0) {
        while (dateList.size() < 7) {
          var empSt = new InlineKeyboardButton();
          empSt.setText(" ");
          empSt.setCallbackData("NONE");
          dateList.add(0, empSt);
        }
      }
      if (dateList.size() < 7) {
        while (dateList.size() < 7) {
          var empEnd = new InlineKeyboardButton();
          empEnd.setText(" ");
          empEnd.setCallbackData("NONE");
          dateList.add(empEnd);
        }
      }
      rowsInLine.add(dateList);
    }

    var previous = new InlineKeyboardButton();
    previous.setText("<<");
    previous.setCallbackData(prevButton+String.valueOf(LocalDate.of(year,monthForOutput,1).minusMonths(1)));
    action.add(previous);
    var next = new InlineKeyboardButton();
    next.setText(">>");
    next.setCallbackData(nextButton+String.valueOf(LocalDate.of(year,monthForOutput,1).plusMonths(1)));
    action.add(next);
    rowsInLine.add(action);

    var back = new InlineKeyboardButton();
    back.setText("Назад");
    back.setCallbackData(backButton);
    backAction.add(back);
    rowsInLine.add(backAction);

    markupInLine.setKeyboard(rowsInLine);
    message.setReplyMarkup(markupInLine);
    message.setMessageId((int) messageId);
    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error(e.getMessage(), e);
    }
  }

  private void executeMessage(SendMessage message) {
    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error(e.getMessage(), e);
    }
  }

  private void prepareAndSendMessage(long chatId, String textToSend) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(textToSend);
    executeMessage(message);
  }
}
