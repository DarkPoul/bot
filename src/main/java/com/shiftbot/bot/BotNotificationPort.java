package com.shiftbot.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface BotNotificationPort {
    void sendMarkdown(Long chatId, String text, InlineKeyboardMarkup markup);
}
