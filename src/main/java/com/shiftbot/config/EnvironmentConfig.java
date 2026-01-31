package com.shiftbot.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class EnvironmentConfig {
    private final Map<String, String> dotEnvContent = new HashMap<>();
    private final String botToken;
    private final String botUsername;
    private final String spreadsheetId;
    private final String credentialsPath;
    private final String auditGroupId;
    private final String adminTelegramId;
    private final ZoneId zoneId;
    private final StorageType storageType;
    private final String dbPath;
    private final String locationsSeed;

    public EnvironmentConfig() {
        loadDotEnv(); // Завантажуємо .env файл при ініціалізації
        
        this.botToken = requiredEnv("BOT_TOKEN");
        this.botUsername = requiredEnv("BOT_USERNAME");
        this.storageType = StorageType.from(getEnvOrDefault("STORAGE", "sqlite"));
        this.dbPath = getEnvOrDefault("DB_PATH", "/data/bot.db");
        this.spreadsheetId = getEnvValue("SPREADSHEET_ID");
        this.credentialsPath = getEnvValue("GOOGLE_APPLICATION_CREDENTIALS");
        this.auditGroupId = requiredEnv("AUDIT_GROUP_ID");
        this.adminTelegramId = requiredEnv("ADMIN_TELEGRAM_ID");
        this.locationsSeed = getEnvValue("LOCATIONS_SEED");
        
        String tz = getEnvOrDefault("TZ", "Europe/Kyiv");
        this.zoneId = ZoneId.of(tz);
    }

    /**
     * Читає файл .env з кореня проекту і зберігає значення в карту.
     */
    private void loadDotEnv() {
        try {
            if (Files.exists(Paths.get(".env"))) {
                try (Stream<String> lines = Files.lines(Paths.get(".env"))) {
                    lines.forEach(line -> {
                        String trimmedLine = line.trim();
                        if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
                            String[] parts = trimmedLine.split("=", 2);
                            if (parts.length == 2) {
                                dotEnvContent.put(parts[0].trim(), parts[1].trim());
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read .env file: " + e.getMessage());
        }
    }

    private String requiredEnv(String key) {
        String value = getEnvValue(key);
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalStateException("Env variable not set: " + key + 
                ". Перевірте наявність змінної в ОС або у файлі .env");
        }
        return value;
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = getEnvValue(key);
        return (Objects.isNull(value) || value.isBlank()) ? defaultValue : value;
    }

    /**
     * Шукає значення спочатку в змінних оточення ОС, потім у файлі .env
     */
    private String getEnvValue(String key) {
        String osEnv = System.getenv(key);
        if (osEnv != null && !osEnv.isBlank()) {
            return osEnv;
        }
        return dotEnvContent.get(key);
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

    public StorageType getStorageType() {
        return storageType;
    }

    public String getDbPath() {
        return dbPath;
    }

    public String getLocationsSeed() {
        return locationsSeed;
    }
}
