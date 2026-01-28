package com.shiftbot.bot.handler;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CoverFlowTest extends FlowTestSupport {

    @Test
    void shouldShowCoverRequestButtonFromMenu() {
        com.shiftbot.model.User user = new com.shiftbot.model.User();
        user.setUserId(111L);
        user.setUsername("cover");
        user.setFullName("Cover User");
        user.setRole(com.shiftbot.model.enums.Role.SELLER);
        user.setStatus(com.shiftbot.model.enums.UserStatus.ACTIVE);
        usersRepository.save(user);
        router.handle(messageUpdate(111L, "cover", "Cover", "User", "🆘 Потрібна заміна"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("🆘 Потрібна заміна? Оберіть дату", message.text());
        InlineKeyboardMarkup markup = message.markup();
        assertNotNull(markup);
        assertTrue(hasButtonWithText(markup, "🚑 Попросити заміну на завтра"));
        assertTrue(hasCallbackWithPrefix(markup, "cover:"));
    }

    @Test
    void shouldCreateCoverRequestOnCallback() {
        LocalDate targetDate = LocalDate.of(2024, 5, 20);
        com.shiftbot.model.User user = new com.shiftbot.model.User();
        user.setUserId(222L);
        user.setUsername("cover2");
        user.setFullName("Cover User");
        user.setRole(com.shiftbot.model.enums.Role.SELLER);
        user.setStatus(com.shiftbot.model.enums.UserStatus.ACTIVE);
        usersRepository.save(user);

        router.handle(callbackUpdate(222L, "cover2", "Cover", "User", "cover:" + targetDate), bot);

        assertEquals(1, requestsRepository.findAll().size());
        Request request = requestsRepository.findAll().get(0);
        assertEquals(RequestType.COVER, request.getType());
        assertEquals(RequestStatus.WAIT_TM, request.getStatus());
        assertEquals(targetDate, request.getDate());
        assertEquals(222L, request.getInitiatorUserId());

        SentMessage message = bot.lastMessage();
        assertEquals("Заявка на заміну створена та очікує ТМ", message.text());
    }
}
