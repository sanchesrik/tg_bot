package com.example.StringBot.service;

import com.example.StringBot.config.BotConfig;
import com.example.StringBot.model.User;
import com.example.StringBot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.N;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.xml.stream.events.Comment;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private UserRepository userRepository;
    final BotConfig config;
    int MOUNTH = 0;
    String for_cal="";
    static final String PREV_BUTTON = "PREV_BUTTON";
    static final String NEXT_BUTTON = "NEXT_BUTTON";
    static final String BACK_BUTTON = "BACK_BUTTON";

    static final String ERROR_TEXT = "Error occurred: ";
    static final String REPL_USER ="Replied to user: ";
    static final String SAVE_USER = "User saved: ";

    public TelegramBot(BotConfig config){
        this.config = config;
        List<BotCommand> listofCommands = new ArrayList();
        listofCommands.add(new BotCommand("/start","get start"));
        listofCommands.add(new BotCommand("/mydata","get my info"));
        listofCommands.add(new BotCommand("/deletedata","del my info"));
        listofCommands.add(new BotCommand("/help","help info"));
        listofCommands.add(new BotCommand("/calendar","inline keyboard"));

        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(),null));
        }
        catch (TelegramApiException e) {
            log.error("Error setting bot's command list" + e.getMessage());
        }
    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()){
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            switch (messageText){
                case "/start":
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/mydata":
                    prepareAndSendMessage(chatId,"ID пользователя " + update.getMessage().getChat().getId());
                    prepareAndSendMessage(chatId,"ID сессии чата " + update.getMessage().getChatId());
                    break;
                case "/help":
                    prepareAndSendMessage(chatId,"Этот бот помогает запланировать и согласовать отпуск, взять соц день или сообщть о больничном.");
                    break;
                case "Соц день":
                    MOUNTH=0;
                    for_cal="Выберите дату соц дня. Нажмите пропустить если соц день необходим сегодня";
                    calendar(chatId, MOUNTH, for_cal);
                    break;
                case "Запланировать отпуск":
                    MOUNTH=0;
                    for_cal="Выберите дату начала отпуска";
                    calendar(chatId, MOUNTH, for_cal);
                    break;
                case "Сообщить о больничном":
                    MOUNTH=0;
                    for_cal="Выберите последний день больничного";
                    calendar(chatId, MOUNTH, for_cal);
                    break;
                default:
                    prepareAndSendMessage(chatId,"ID сессии чата " + update.getMessage().getChat().getId());
            }
        }
        else if (update.hasCallbackQuery()){
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            if(callbackData.equals(PREV_BUTTON)){
                MOUNTH=MOUNTH-1;
                executeEditCalendar(chatId,messageId, MOUNTH,for_cal);
            }
            else if(callbackData.equals(NEXT_BUTTON)){
                MOUNTH=MOUNTH+1;
                executeEditCalendar(chatId,messageId, MOUNTH,for_cal);
            }
            else if(callbackData.equals(BACK_BUTTON)){

                backAction(chatId,messageId);
            }
        }
    }

    private void backAction(long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите другой пункт меню ");
        message.setMessageId((int)messageId);
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT +String.valueOf(e.getMessage()));
        }
    }

    private void calendar(long chatId, int mon, String text){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        Calendar myCalendar = Calendar.getInstance();
        myCalendar.add(Calendar.MONTH,mon);
        String month = new SimpleDateFormat("MMMM", new Locale("ru")).format(myCalendar.getTime());
        int year = myCalendar.get(Calendar.YEAR);
        int month_for = myCalendar.get(Calendar.MONTH);

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowdays = new ArrayList<>();
        List<InlineKeyboardButton> action = new ArrayList<>();
        List<InlineKeyboardButton> back_butt = new ArrayList<>();

        var button = new InlineKeyboardButton();
        month=month.substring(0,1).toUpperCase() + month.substring(1);
        button.setText(month+' '+year);
        button.setCallbackData("NONE");
        rowInLine.add(button);
        rowsInLine.add(rowInLine);

        List<String> anotherList = Arrays.asList("ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС");
        for (String day : anotherList){
            var day_of_week = new InlineKeyboardButton();
            day_of_week.setText(day);
            day_of_week.setCallbackData("NONE");
            rowdays.add(day_of_week);
        }
        rowsInLine.add(rowdays);

        int max_date = myCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        String dates = "";
        for (int i = 1; i <= max_date; i++) {
            myCalendar.set(year, month_for, i);
            int day = myCalendar.get(Calendar.DAY_OF_WEEK);
            dates += String.valueOf(i);

            if (i < max_date) {
                dates += ", ";
            }
            if (day  == 1) {
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
            if (dateList.size() < 7 && i==0) {
                while(dateList.size() < 7){
                    var emp_st = new InlineKeyboardButton();
                    emp_st.setText(" ");
                    emp_st.setCallbackData("NONE");
                    dateList.add(0,emp_st);
                }
            }
            if (dateList.size() < 7) {
                while(dateList.size() < 7){
                    var emp_end = new InlineKeyboardButton();
                    emp_end.setText(" ");
                    emp_end.setCallbackData("NONE");
                    dateList.add(emp_end);
                }
            }
            rowsInLine.add(dateList);
        }


        var prev = new InlineKeyboardButton();
        prev.setText("<<");
        prev.setCallbackData(PREV_BUTTON);
        action.add(prev);
        var next = new InlineKeyboardButton();
        next.setText(">>");
        next.setCallbackData(NEXT_BUTTON);
        action.add(next);
        rowsInLine.add(action);

        var back = new InlineKeyboardButton();
        back.setText("Назад");
        back.setCallbackData(BACK_BUTTON);
        back_butt.add(back);
        rowsInLine.add(back_butt);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);
        executeMessage(message);
    }

    private void registerUser(Message msg) {
        if(userRepository.findById(msg.getChatId()).isEmpty()){
            var chatId = msg.getChatId();
            var chat = msg.getChat();
            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            userRepository.save(user);
            log.info(SAVE_USER+ user);
        }
    }

    private void startCommandReceived(long chatId, String name){
        String answer = "Личность подтверждена, добро пожаловать " +name;
        log.info(REPL_USER+String.valueOf(name));
        sendMessage(chatId,answer);
    }
    private void sendMessage(long chatId, String textToSend){
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
    private void executeEditCalendar(Long chatId, long messageId,int mon,String text){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        Calendar myCalendar = Calendar.getInstance();
        myCalendar.add(Calendar.MONTH,mon);
        String month = new SimpleDateFormat("MMMM", new Locale("ru")).format(myCalendar.getTime());
        int year = myCalendar.get(Calendar.YEAR);
        int month_for = myCalendar.get(Calendar.MONTH);

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowdays = new ArrayList<>();
        List<InlineKeyboardButton> action = new ArrayList<>();
        List<InlineKeyboardButton> back_butt = new ArrayList<>();

        var button = new InlineKeyboardButton();
        month=month.substring(0,1).toUpperCase() + month.substring(1);
        button.setText(month+' '+year);
        button.setCallbackData("NONE");
        rowInLine.add(button);
        rowsInLine.add(rowInLine);

        List<String> anotherList = Arrays.asList("ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС");
        for (String day : anotherList){
            var day_of_week = new InlineKeyboardButton();
            day_of_week.setText(day);
            day_of_week.setCallbackData("NONE");
            rowdays.add(day_of_week);
        }
        rowsInLine.add(rowdays);

        int max_date = myCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        String dates = "";
        for (int i = 1; i <= max_date; i++) {
            myCalendar.set(year, month_for, i);
            int day = myCalendar.get(Calendar.DAY_OF_WEEK);
            dates += String.valueOf(i);

            if (i < max_date) {
                dates += ", ";
            }
            if (day  == 1) {
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
            if (dateList.size() < 7 && i==0) {
                while(dateList.size() < 7){
                    var emp_st = new InlineKeyboardButton();
                    emp_st.setText(" ");
                    emp_st.setCallbackData("NONE");
                    dateList.add(0,emp_st);
                }
            }
            if (dateList.size() < 7) {
                while(dateList.size() < 7){
                    var emp_end = new InlineKeyboardButton();
                    emp_end.setText(" ");
                    emp_end.setCallbackData("NONE");
                    dateList.add(emp_end);
                }
            }
            rowsInLine.add(dateList);
        }

        var prev = new InlineKeyboardButton();
        prev.setText("<<");
        prev.setCallbackData(PREV_BUTTON);
        action.add(prev);
        var next = new InlineKeyboardButton();
        next.setText(">>");
        next.setCallbackData(NEXT_BUTTON);
        action.add(next);
        rowsInLine.add(action);

        var back = new InlineKeyboardButton();
        back.setText("Назад");
        back.setCallbackData(BACK_BUTTON);
        back_butt.add(back);
        rowsInLine.add(back_butt);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);
        message.setMessageId((int)messageId);
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT +String.valueOf(e.getMessage()));
        }
    }

    private void executeMessage(SendMessage message){
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT +String.valueOf(e.getMessage()));
        }
    }
    private void prepareAndSendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }
}
