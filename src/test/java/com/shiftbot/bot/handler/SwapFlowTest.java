package com.shiftbot.bot.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwapFlowTest extends FlowTestSupport {

    @Test
    void shouldReturnNotImplementedMessageOnSwapCallback() {
        com.shiftbot.model.User user = new com.shiftbot.model.User();
        user.setUserId(333L);
        user.setUsername("swap");
        user.setFullName("Swap User");
        user.setRole(com.shiftbot.model.enums.Role.SELLER);
        user.setStatus(com.shiftbot.model.enums.UserStatus.APPROVED);
        usersRepository.save(user);
        router.handle(callbackUpdate(333L, "swap", "Swap", "User", "M::swap"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("Меню в розробці", message.text());
        assertNull(message.markup());
    }

    @Test
    void shouldPromptMenuForSwapTextMessage() {
        com.shiftbot.model.User user = new com.shiftbot.model.User();
        user.setUserId(444L);
        user.setUsername("swap2");
        user.setFullName("Swap User");
        user.setRole(com.shiftbot.model.enums.Role.SELLER);
        user.setStatus(com.shiftbot.model.enums.UserStatus.APPROVED);
        usersRepository.save(user);
        router.handle(messageUpdate(444L, "swap2", "Swap", "User", "🔁 Підміни"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("Оберіть дію з меню нижче", message.text());
        assertNotNull(message.markup());
    }
}
