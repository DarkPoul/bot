package com.shiftbot.bot.handler;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.*;

class ActivationFlowTest extends FlowTestSupport {

    @Test
    void shouldOnboardNewUserAndShowSellerMenu() {
        router.handle(messageUpdate(101L, "alice", "Alice", "A", "/start"), bot);

        SentMessage message = bot.lastMessage();
        assertTrue(message.text().contains("Вітаємо, Alice A"), "welcome text should include full name");

        InlineKeyboardMarkup markup = message.markup();
        assertNotNull(markup);
        assertTrue(hasButtonWithText(markup, "📅 Мій графік"));
        assertTrue(hasButtonWithText(markup, "🆘 Потрібна заміна"));
        assertFalse(hasButtonWithText(markup, "📥 Мої заявки"), "seller should not see TM menu item");

        assertEquals(1, usersRepository.findAll().size());
        User saved = usersRepository.findAll().get(0);
        assertEquals(Role.SELLER, saved.getRole());
        assertEquals(UserStatus.PENDING, saved.getStatus());
    }

    @Test
    void shouldShowTmMenuWithRequestsItem() {
        User tm = new User();
        tm.setUserId(202L);
        tm.setUsername("tm");
        tm.setFullName("Team Manager");
        tm.setRole(Role.TM);
        tm.setStatus(UserStatus.ACTIVE);
        usersRepository.save(tm);

        router.handle(messageUpdate(202L, "tm", "Team", "Manager", "/start"), bot);

        SentMessage message = bot.lastMessage();
        InlineKeyboardMarkup markup = message.markup();
        assertNotNull(markup);
        assertTrue(hasButtonWithText(markup, "📥 Мої заявки"), "TM should see requests menu item");
    }

    @Test
    void shouldPromptForMenuOnUnknownInput() {
        router.handle(messageUpdate(303L, "bob", "Bob", "B", "random"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("Оберіть дію з меню нижче", message.text());
        InlineKeyboardMarkup markup = message.markup();
        assertNotNull(markup);
        assertTrue(hasCallbackWithPrefix(markup, "M::"));
    }
}
