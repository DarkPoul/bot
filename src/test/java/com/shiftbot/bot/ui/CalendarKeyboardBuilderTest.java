package com.shiftbot.bot.ui;

import com.shiftbot.model.enums.ShiftStatus;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalendarKeyboardBuilderTest {

    @Test
    void buildsSixWeeksGrid() {
        CalendarKeyboardBuilder builder = new CalendarKeyboardBuilder();
        LocalDate march2024 = LocalDate.of(2024, 3, 1);
        InlineKeyboardMarkup markup = builder.buildMonth(march2024, Map.of(march2024, ShiftStatus.APPROVED), "calendar:");
        assertNotNull(markup.getKeyboard());
        // header + rows
        assertTrue(markup.getKeyboard().size() >= 5);
        assertEquals(7, markup.getKeyboard().get(0).size());
    }

    @Test
    void includesCallbackPrefixWithLocation() {
        CalendarKeyboardBuilder builder = new CalendarKeyboardBuilder();
        LocalDate march2024 = LocalDate.of(2024, 3, 1);
        InlineKeyboardMarkup markup = builder.buildMonth(march2024, Map.of(march2024, ShiftStatus.DRAFT), "location:loc-1:");
        String callbackData = markup.getKeyboard().get(1).stream()
                .filter(btn -> btn.getCallbackData().startsWith("location:loc-1:"))
                .findFirst()
                .map(InlineKeyboardButton::getCallbackData)
                .orElse("");
        assertTrue(callbackData.contains("location:loc-1:"));
    }
}
