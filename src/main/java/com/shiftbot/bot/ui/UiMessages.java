package com.shiftbot.bot.ui;

public final class UiMessages {
    public static final String PROMPT_DATE = "Введіть дату у форматі dd.MM (наприклад, 05.07). Щоб скасувати, надішліть cancel.";
    public static final String PROMPT_START_TIME = "Введіть час початку у форматі HH:mm (наприклад, 09:30).";
    public static final String PROMPT_END_TIME = "Введіть час завершення у форматі HH:mm (наприклад, 18:00).";
    public static final String INVALID_DATE_FORMAT = "Не вдалося розпізнати дату. Використайте формат dd.MM, наприклад 05.07.";
    public static final String INVALID_TIME_FORMAT = "Не вдалося розпізнати час. Використайте формат HH:mm, наприклад 09:30.";
    public static final String INVALID_TIME_RANGE = "Час завершення має бути пізніше за час початку. Спробуйте ще раз.";
    public static final String CONVERSATION_CANCELLED = "Дію скасовано. Щоб почати знову, оберіть команду у меню.";
    public static final String CONVERSATION_TIMEOUT = "Попередня сесія закінчилася за часом. Запустіть дію ще раз.";
    public static final String NO_ACTIVE_CONVERSATION = "Активна сесія не знайдена. Почніть дію з меню.";
    public static final String NOOP_MESSAGE = "Ця кнопка лише для відображення. Оберіть інший пункт.";
    public static final String REQUEST_CREATED = "Заявка на заміну створена. Очікує підтвердження ТМ.";

    private UiMessages() {
    }
}
