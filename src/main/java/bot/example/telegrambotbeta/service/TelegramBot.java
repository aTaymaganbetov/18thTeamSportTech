package bot.example.telegrambotbeta.service;


import bot.example.telegrambotbeta.config.BotConfig;
import bot.example.telegrambotbeta.model.*;
import com.vdurmont.emoji.EmojiParser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdsRepository adsRepository;
    final BotConfig config;

    static final String HELP_TEXT = "This bot is created to demonstrate Spring capabilities.\n\n" +
            "You can execute commands from the main menu on the left or by typing a command:\n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /mydata to see data stored about yourself\n\n" +
            "Type /help to see this message again";

    static final String ORGANIZER_BUTTON = "ORGANIZER_BUTTON";
    static final String PARTICIPANT_BUTTON = "PARTICIPANT_BUTTON";

    static final String ERROR_TEXT = "Error occurred: ";

    public EventDTO eventDTO;

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/test", "this function is for testing"));
        listofCommands.add(new BotCommand("/start", "get a welcome message"));
        listofCommands.add(new BotCommand("/mydata", "get your data stored"));
        listofCommands.add(new BotCommand("/help", "info how to use this bot"));
        listofCommands.add(new BotCommand("/settings", "set your preferences"));
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
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

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if(messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user: users){
                    prepareAndSendMessage(user.getChatId(), textToSend);
                }
            } else if(isDateFormatValid(messageText.trim(), "dd.MM.yyyy")) {
                eventDTO.setDay(messageText.trim());
                getTime(chatId);
            } else if(messageText.equalsIgnoreCase("Ночью")) {
                inNight(chatId, update.getMessage().getChat().getFirstName());
            } else if(messageText.equalsIgnoreCase("Утром")) {
                inMorning(chatId, update.getMessage().getChat().getFirstName());
            } else if(messageText.equalsIgnoreCase("Вечером")) {
                inEvening(chatId, update.getMessage().getChat().getFirstName());
            } else if(messageText.equalsIgnoreCase("Днем")) {
                inDay(chatId, update.getMessage().getChat().getFirstName());
            } else if(is24HourTime(messageText.trim())) {
                eventDTO.setTime(messageText.trim());
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Вы выбрали день: " + eventDTO.getDay() + ", время: " + eventDTO.getTime() + ". Матч будет организован в указанное время.\nВы хотите продолжить или изменить время?");
                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                List<KeyboardRow> keyboardRows = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add("Продолжить");
                keyboardRows.add(row);
                row = new KeyboardRow();
                row.add("Изменить время");
                keyboardRows.add(row);
                keyboardMarkup.setKeyboard(keyboardRows);
                message.setReplyMarkup(keyboardMarkup);
                executeMessage(message);
            } else if(isAddressFormatValid(messageText.trim().toLowerCase())) {
                String [] array = messageText.split(":");
                String address = array[1].trim();
                eventDTO.setLocation(address);
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Ваш адрес: " + address + ". Вы хотите продолжить далее или изменить адрес?");
                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                List<KeyboardRow> keyboardRows = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add("Далее");
                keyboardRows.add(row);
                row = new KeyboardRow();
                row.add("Изменить адрес");
                keyboardRows.add(row);
                keyboardMarkup.setKeyboard(keyboardRows);
                message.setReplyMarkup(keyboardMarkup);
                executeMessage(message);
            } else if(isMoneyFormatValid(messageText.trim())) {
                String [] array = messageText.split("тг");
                double amount = Double.parseDouble(array[0]);
                eventDTO.setAmount(amount);
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Сумма: " + amount + " тг");
                executeMessage(message);

                SendMessage message2 = new SendMessage();
                message2.setChatId(String.valueOf(chatId));
                String key = "230802";
                message2.setText("Ваш ключ: " + key + ". Отправьте этот ключ участникам, чтобы они присоединились к вашему event-у");
                eventDTO.setEventKey(key);
                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                List<KeyboardRow> keyboardRows = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add("Посмотреть список присоединившихся участников");
                keyboardRows.add(row);
                row = new KeyboardRow();
                row.add("Отменить event");
                keyboardRows.add(row);
                keyboardMarkup.setKeyboard(keyboardRows);
                message2.setReplyMarkup(keyboardMarkup);
                executeMessage(message2);
            } else if(isKeyFormatValid(messageText.trim())) {
                String [] array = messageText.split(":");
                String key = array[1].trim();

                if(key.equals(eventDTO.getEventKey())) {
                    User newUser = new User();
                    newUser.setChatId(chatId);
                    newUser.setFirstName(update.getMessage().getChat().getFirstName());
                    newUser.setLastName(update.getMessage().getChat().getLastName());
                    newUser.setUserName(update.getMessage().getChat().getUserName());
                    newUser.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
                    newUser.setRole("PARTICIPANT");
                    eventDTO.addParticipant(newUser);

                    SendMessage message2 = new SendMessage();
                    message2.setChatId(String.valueOf(chatId));
                    message2.setText("Вы успешно добавлены в event! Ожидайте рассылку от организатора...");
                    ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                    List<KeyboardRow> keyboardRows = new ArrayList<>();
                    KeyboardRow row = new KeyboardRow();
                    row.add("Отменить участие");
                    keyboardRows.add(row);
                    keyboardMarkup.setKeyboard(keyboardRows);
                    message2.setReplyMarkup(keyboardMarkup);
                    executeMessage(message2);
                } else {
                    SendMessage message2 = new SendMessage();
                    message2.setChatId(String.valueOf(chatId));
                    message2.setText("Вы не добавлены в event, введенный ключ не существует в базе. Повторите попытку.");
                    executeMessage(message2);
                }
            }

            else {

                switch (messageText) {
                    case "/test":
                        forOrganizer(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/start":
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/help":

                        prepareAndSendMessage(chatId, HELP_TEXT);
                        break;

                    case "/register":

                        register(chatId);
                        break;

                    case "Ночью":
                        inNight(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "Утром":
                        inMorning(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "Днем":
                        inDay(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "Вечером":
                        inEvening(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "Изменить время":
                        forOrganizerRepeat(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "Изменить адрес":
                        getLocation(chatId);
                        break;

                    case "Продолжить":
                        getLocation(chatId);
                        break;

                    case "Далее":
                        sendMessage(chatId, "Введите необходимую сумму, которую нужно собрать (в тенге)");
                        break;

                    case "Отменить event":
                        eventDTO = new EventDTO();
                        sendMessage(chatId, "Введите необходимую сумму, которую нужно собрать (в тенге)");
                        break;

                    case "Посмотреть список присоединившихся участников":
                        StringBuilder text = new StringBuilder("Список участников в реальном времени:");
                        Map<Long, User> participants = eventDTO.getParticipants();

                        Collection<User> users = participants.values();
                        int counter = 1;
                        for (User user : users) {
                            text.append(counter + ". " + user.getFirstName() + " (@" + user.getUserName()+")\n");
                            counter++;
                        }

                        sendMessage(chatId, text.toString());
                        break;
                    case "Отменить участие":
                        eventDTO.removeParticipant(chatId);
                        break;

                    default:
                        if(!isDateFormatValid(messageText, "dd.MM.yyyy") &&
                                !is24HourTime(messageText.trim()) && !isAddressFormatValid(messageText.trim())
                        && !isMoneyFormatValid(messageText.trim())) {
                            sendMessage(chatId, "Ошибка! Проверьте правильность введенных данных )");
                        }

                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if(callbackData.equals(ORGANIZER_BUTTON)){
                String text = "Вы успешно зарегистрировались как ОРГАНИЗАТОР";
                registerUser(update.getCallbackQuery().getMessage(), "ORGANIZER");
                executeEditMessageText(text, chatId, messageId);
                forOrganizer(chatId, update.getCallbackQuery().getMessage().getChat().getFirstName());
            }
            else if(callbackData.equals(PARTICIPANT_BUTTON)){
                registerUser(update.getCallbackQuery().getMessage(), "PARTICIPANT");
                String text = "Вы успешно зарегистрировались как УЧАСТНИК";
                executeEditMessageText(text, chatId, messageId);
            }
        }


    }

    private void register(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Do you really want to register?");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData(ORGANIZER_BUTTON);

        var noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData(PARTICIPANT_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    private void registerUser(Message msg, String role) {
        var chatId = msg.getChatId();
        var chat = msg.getChat();

        Optional<User> optionalUser = userRepository.findById(chatId);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            // Обновление необходимых полей пользователя
            user.setRole(role);
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            // ...

            userRepository.save(user);
            log.info("user updated: " + user);
        } else {
            User newUser = new User();
            newUser.setChatId(chatId);
            newUser.setFirstName(chat.getFirstName());
            newUser.setLastName(chat.getLastName());
            newUser.setUserName(chat.getUserName());
            newUser.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            newUser.setRole(role);

            userRepository.save(newUser);
            log.info("user saved: " + newUser);
        }
    }

    private void startCommandReceived(long chatId, String name) {

        String answer = EmojiParser.parseToUnicode("Привет, " + name + "!" + " :blush: ");
        String answer2 = EmojiParser.parseToUnicode("Вы хотите зарегистрироваться как организатор или участник?");
        log.info("Replied to user " + name);
        sendMessage(chatId, answer);

        // -----------------------------------------
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer2);

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Организатор");
        yesButton.setCallbackData(ORGANIZER_BUTTON);

        var noButton = new InlineKeyboardButton();

        noButton.setText("Участник");
        noButton.setCallbackData(PARTICIPANT_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    private void forParticipant(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                name + ", " + " введите ключ в формате <ключ: ваш ключ> (например, \"ключ: 4528\"), который прислал вам организатор, чтобы присоединиться к event-у. :blush: "
                );
        sendMessage(chatId, answer);
    }

    private void forOrganizer(long chatId, String name) {

        eventDTO = new EventDTO();

        String answer = EmojiParser.parseToUnicode(name + ", " + " предоставьте информацию о предстоящей игре в роли организатора :blush: " +
                "Я буду задавать вопросы, а ты вводи ответы на них");
        sendMessage(chatId, answer);

        // ------------------------------------------------
        String answer2 = EmojiParser.parseToUnicode(
                "Напишите день проведения игры в формате \"dd.MM.yyyy\" (например, \"31.12.2022\") :blush: "
        );
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer2);
        executeMessage(message);
    }

    private void forOrganizerRepeat(long chatId, String name) {
        // ------------------------------------------------
        String answer2 = EmojiParser.parseToUnicode(
                "Напишите день проведения игры в формате \"dd.MM.yyyy\" (например, \"31.12.2022\") :blush: "
        );
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer2);

        executeMessage(message);
    }



    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }


    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void prepareAndSendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }

    @Scheduled(cron = "${cron.scheduler}")
    private void sendAds(){

        var ads = adsRepository.findAll();
        var users = userRepository.findAll();

        for(Ads ad: ads) {
            for (User user: users){
                prepareAndSendMessage(user.getChatId(), ad.getAd());
            }
        }
    }

    public void getLocation(Long chatId) {
        String answer2 = EmojiParser.parseToUnicode(
                "В каком месте планируете организовать матч? Напишите точный адрес в формате: <адрес: ваш адрес> (например, \"адрес: Г. Караганда, ул. Алалыкина 12\")"
        );

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer2);

        executeMessage(message);
    }

    public void getTime(Long chatId) {
        String answer2 = EmojiParser.parseToUnicode(
                "В какое время планируете организовать матч? "
        );

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer2);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Ночью");
        row.add("Утром");
        keyboardRows.add(row);
        row = new KeyboardRow();
        row.add("Днем");
        row.add("Вечером");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        // Установка клавиатуры в сообщении
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    public void inNight(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                name + ", " + " выберите время начала игры :blush: "
        );
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("00:00");
        row.add("00:30");
        row.add("01:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("01:30");
        row.add("02:00");
        row.add("02:30");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("03:00");
        row.add("03:30");
        row.add("04:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("04:30");
        row.add("05:00");
        row.add("05:30");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        // Установка клавиатуры в сообщении
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    public void inMorning(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                name + ", " + " выберите время начала игры :blush: "
        );
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("06:00");
        row.add("06:30");
        row.add("07:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("07:30");
        row.add("08:00");
        row.add("08:30");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("09:00");
        row.add("09:30");
        row.add("10:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("10:30");
        row.add("11:00");
        row.add("11:30");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        // Установка клавиатуры в сообщении
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }
    public void inDay(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                name + ", " + " выберите время начала игры :blush: "
        );
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("12:00");
        row.add("12:30");
        row.add("13:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("13:30");
        row.add("14:00");
        row.add("14:30");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("15:00");
        row.add("15:30");
        row.add("16:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("16:30");
        row.add("17:00");
        row.add("17:30");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        // Установка клавиатуры в сообщении
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    public void inEvening(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                name + ", " + " выберите время начала игры :blush: "
        );
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("18:00");
        row.add("18:30");
        row.add("19:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("19:30");
        row.add("20:00");
        row.add("20:30");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("21:00");
        row.add("21:30");
        row.add("22:00");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("22:30");
        row.add("23:00");
        row.add("23:30");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        // Установка клавиатуры в сообщении
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    public static boolean isDateFormatValid(String inputDate, String dateFormat) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
            sdf.setLenient(false);
            sdf.parse(inputDate);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    public static boolean is24HourTime(String inputTime) {
        String timeFormatRegex = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$";
        Pattern pattern = Pattern.compile(timeFormatRegex);
        return pattern.matcher(inputTime).matches();
    }

    public static boolean isAddressFormatValid(String input) {
        String addressFormatRegex = "\\b(?i)адрес\\b\\s*:\\s*(.+)";
        Pattern pattern = Pattern.compile(addressFormatRegex);
        return pattern.matcher(input).matches();
    }

    public static boolean isMoneyFormatValid(String input) {
        String addressFormatRegex = "^[0-9]+(\\.[0-9]{1,2})?\\s*тг";
        Pattern pattern = Pattern.compile(addressFormatRegex);
        return pattern.matcher(input).matches();
    }

    public static boolean isKeyFormatValid(String input) {
        String addressFormatRegex = "\\b(?i)ключ\\b\\s*:\\s*(.+)";
        Pattern pattern = Pattern.compile(addressFormatRegex);
        return pattern.matcher(input).matches();
    }
}
