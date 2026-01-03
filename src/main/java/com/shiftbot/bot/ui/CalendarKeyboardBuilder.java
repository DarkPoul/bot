package com.shiftbot.bot.ui;

import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.util.TimeUtils;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CalendarKeyboardBuilder {

    public InlineKeyboardMarkup buildMonth(LocalDate month, Map<LocalDate, ShiftStatus> statuses, String callbackPrefix) {
        LocalDate first = month.withDayOfMonth(1);
        DayOfWeek firstDayOfWeek = first.getDayOfWeek();
        int shift = (firstDayOfWeek.getValue() + 6) % 7; // make Monday=0
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // header with weekdays
        List<InlineKeyboardButton> header = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            header.add(InlineKeyboardButton.builder()
                    .text(TimeUtils.ukrainianDayOfWeek(dow))
                    .callbackData("noop")
                    .build());
        }
        rows.add(header);

        int daysInMonth = month.lengthOfMonth();
        int day = 1;
        for (int week = 0; week < 6; week++) {
            List<InlineKeyboardButton> weekRow = new ArrayList<>();
            for (int dow = 0; dow < 7; dow++) {
                if (week == 0 && dow < shift) {
                    weekRow.add(emptyCell());
                } else if (day > daysInMonth) {
                    weekRow.add(emptyCell());
                } else {
                    LocalDate currentDate = first.withDayOfMonth(day);
                    String icon = statusIcon(statuses != null ? statuses.get(currentDate) : null);
                    weekRow.add(InlineKeyboardButton.builder()
                            .text(icon + day)
                            .callbackData(callbackPrefix + currentDate)
                            .build());
                    day++;
                }
            }
            rows.add(weekRow);
            if (day > daysInMonth) {
                break;
            }
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton emptyCell() {
        return InlineKeyboardButton.builder().text(" ").callbackData("noop").build();
    }

    private String statusIcon(ShiftStatus status) {
        if (status == null) return "⬜ ";
        return switch (status) {
            case APPROVED -> "🟥 ";
            case DRAFT, PENDING_TM -> "🟩 ";
            case CANCELED -> "⬜ ";
        };
    }
}
