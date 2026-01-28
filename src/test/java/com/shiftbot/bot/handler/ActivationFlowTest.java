package com.shiftbot.bot.handler;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.*;

class ActivationFlowTest extends FlowTestSupport {

    @Test
    void shouldRequestNameAndLocationForNewUser() {
        locationsRepository.save(new com.shiftbot.model.Location("loc-1", "Mall", "", true));
        router.handle(messageUpdate(101L, "alice", "Alice", "A", "/start"), bot);

        SentMessage message = bot.lastMessage();
        assertTrue(message.text().contains("введіть ПІБ"), "should request full name");

        router.handle(messageUpdate(101L, "alice", "Alice", "A", "Іван Петренко"), bot);
        InlineKeyboardMarkup markup = bot.lastMessage().markup();
        assertNotNull(markup);
        assertTrue(hasCallbackWithPrefix(markup, "onboard:loc:"));

        router.handle(callbackUpdate(101L, "alice", "Alice", "A", "onboard:loc:loc-1"), bot);
        SentMessage done = bot.lastMessage();
        assertTrue(done.text().contains("анкета"), "should confirm registration");
        assertEquals(1, usersRepository.findAll().size());
        User saved = usersRepository.findAll().get(0);
        assertEquals(Role.SELLER, saved.getRole());
        assertEquals(UserStatus.PENDING, saved.getStatus());
        assertEquals("Іван Петренко", saved.getFullName());
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
        User seller = new User();
        seller.setUserId(303L);
        seller.setUsername("bob");
        seller.setFullName("Bob B");
        seller.setRole(Role.SELLER);
        seller.setStatus(UserStatus.ACTIVE);
        usersRepository.save(seller);
        router.handle(messageUpdate(303L, "bob", "Bob", "B", "random"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("Оберіть дію з меню нижче", message.text());
        InlineKeyboardMarkup markup = message.markup();
        assertNotNull(markup);
        assertTrue(hasCallbackWithPrefix(markup, "M::"));
    }
}
