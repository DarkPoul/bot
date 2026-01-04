package com.shiftbot.bot.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwapFlowTest extends FlowTestSupport {

    @Test
    void shouldReturnNotImplementedMessageOnSwapCallback() {
        router.handle(callbackUpdate(333L, "swap", "Swap", "User", "M::swap"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("Меню в розробці", message.text());
        assertNull(message.markup());
    }

    @Test
    void shouldPromptMenuForSwapTextMessage() {
        router.handle(messageUpdate(444L, "swap2", "Swap", "User", "🔁 Підміни"), bot);

        SentMessage message = bot.lastMessage();
        assertEquals("Оберіть дію з меню нижче", message.text());
        assertNotNull(message.markup());
    }
}
