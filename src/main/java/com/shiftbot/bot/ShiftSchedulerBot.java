package com.shiftbot.bot;

import com.shiftbot.bot.handler.UpdateRouter;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShiftSchedulerBot extends TelegramLongPollingBot implements BotNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(ShiftSchedulerBot.class);
    private final String token;
    private final String username;
    private final UpdateRouter router;

    public ShiftSchedulerBot(String token, String username, UpdateRouter router) {
        super(token);
        this.token = token;
        this.username = username;
        this.router = router;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            router.handle(update, this);
        } catch (Exception e) {
            log.error("Failed to handle update", e);
        }
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void sendMarkdown(Long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode(ParseMode.MARKDOWNV2);
        if (markup != null) {
            message.setReplyMarkup(markup);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message", e);
        }
    }
}
