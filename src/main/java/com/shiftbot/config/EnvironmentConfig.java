package com.shiftbot.config;

import java.time.ZoneId;
import java.util.Objects;

public class EnvironmentConfig {
    private final String botToken;
    private final String botUsername;
    private final String spreadsheetId;
    private final String credentialsPath;
    private final String auditGroupId;
    private final String adminTelegramId;
    private final ZoneId zoneId;

    public EnvironmentConfig() {
        this.botToken = requiredEnv("BOT_TOKEN");
        this.botUsername = requiredEnv("BOT_USERNAME");
        this.spreadsheetId = requiredEnv("SPREADSHEET_ID");
        this.credentialsPath = requiredEnv("GOOGLE_APPLICATION_CREDENTIALS");
        this.auditGroupId = requiredEnv("AUDIT_GROUP_ID");
        this.adminTelegramId = requiredEnv("ADMIN_TELEGRAM_ID");
        String tz = System.getenv().getOrDefault("TZ", "Europe/Kyiv");
        this.zoneId = ZoneId.of(tz);
    }

    private String requiredEnv(String key) {
        String value = System.getenv(key);
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalStateException("Env variable not set: " + key);
        }
        return value;
    }

    public String getBotToken() {
        return botToken;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public String getAuditGroupId() {
        return auditGroupId;
    }

    public String getAdminTelegramId() {
        return adminTelegramId;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
